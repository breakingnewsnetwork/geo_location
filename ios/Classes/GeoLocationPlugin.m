#import "GeoLocationPlugin.h"
#import <CoreLocation/CoreLocation.h>

@implementation GeoLocationPlugin {
    CLLocationManager *_locationManager;
    FlutterEngine *_headlessRunner;
    FlutterMethodChannel *_callbackChannel;
    FlutterMethodChannel *_mainChannel;
    NSObject<FlutterPluginRegistrar> *_registrar;
    NSUserDefaults *_persistentState;
    NSMutableArray *_eventQueue;
    int64_t _onLocationUpdateHandle;
}

static const NSString *kLocationKey = @"location";
static GeoLocationPlugin *instance = nil;
static FlutterPluginRegistrantCallback registerPlugins = nil;
static BOOL initialized = NO;

#pragma mark FlutterPlugin Methods

+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar> *)registrar {
    @synchronized(self) {
        NSLog(@"GeoLocationPlugin - registerWithRegistrar");
        if (instance == nil) {
            instance = [[GeoLocationPlugin alloc] init:registrar];
            [registrar addApplicationDelegate:instance];
        }
    }
}

+ (void)setPluginRegistrantCallback:(FlutterPluginRegistrantCallback)callback {
    NSLog(@"GeoLocationPlugin - setPluginRegistrantCallback");
    registerPlugins = callback;
}

- (void)handleMethodCall:(FlutterMethodCall *)call result:(FlutterResult)result {
    NSLog(@"GeoLocationPlugin - handleMethodCall: %@\n", call.method);
    NSArray *arguments = call.arguments;
    if ([@"LocationUpdatesService.initializeService" isEqualToString:call.method]) {
        NSAssert(arguments.count == 1, @"Invalid argument count for 'LocationUpdatesService.initializeService'");
        [self startGeoLocationService:[arguments[0] longValue]];
        result(@(YES));
    } else if ([@"LocationUpdatesService.initialized" isEqualToString:call.method]) {
        @synchronized(self) {
            initialized = YES;
            // Send the geolocation events that occurred while the background isolate was initializing.
            while ([_eventQueue count] > 0) {
                NSDictionary* locationDic = _eventQueue[0];
                [_eventQueue removeObjectAtIndex:0];
                CLLocation* location = [locationDic objectForKey:kLocationKey];
                [self onLocationEvent:location];
            }
        }
        result(nil);
    } else if ([@"LocationUpdatesService.registerGeoLocation" isEqualToString:call.method]) {
        [self registerGeoLocation:arguments];
        result(@(YES));
    } else if ([@"LocationUpdatesService.removeGeoLocation" isEqualToString:call.method]) {
        result(@([self removeGeoLocation:arguments]));
    } else {
        result(FlutterMethodNotImplemented);
    }
}

- (BOOL)application:(UIApplication *)application
didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    NSLog(@"GeoLocationPlugin - didFinishLaunchingWithOptions");
    // Check to see if we're being launched due to a location event.
    if (launchOptions[UIApplicationLaunchOptionsLocationKey] != nil) {
        // Restart the headless service.
        NSLog(@"GeoLocationPlugin - didFinishLaunchingWithOptions - Restart the headless service.");
        [self startGeoLocationService:[self getCallbackDispatcherHandle]];
        [self->_locationManager startMonitoringSignificantLocationChanges];
    }
    
    // Note: if we return NO, this vetos the launch of the application.
    return YES;
}

#pragma mark LocationManagerDelegate Methods
- (void)locationManager:(CLLocationManager *)manager
     didUpdateLocations:(NSArray<CLLocation *> *)locations {
    NSLog(@"GeoLocationPlugin - didUpdateLocations");
    CLLocation *location = locations.lastObject;
    if(initialized) {
        [self onLocationEvent:location];
    } else {
        NSDictionary *dict = @{kLocationKey: location};
        [_eventQueue addObject:dict];
    }
}

#pragma mark GeoLocationPlugin Methods

- (void)onLocationEvent:(CLLocation *)location {
    NSLog(@"GeoLocationPlugin - onLocationEvent");
    int64_t handle = [self getLocationCallbackHandle];
    NSString* username = [self getGeoUsername];
    NSString* deviceId = [self getGeoDeviceId];
    [_callbackChannel invokeMethod:@""
     arguments:@[@(handle),
                 @[ @(location.coordinate.latitude), @(location.coordinate.longitude) ],
                 @[ username, deviceId]
                 ]];
}

- (instancetype)init:(NSObject<FlutterPluginRegistrar> *)registrar {
    self = [super init];
    NSLog(@"GeoLocationPlugin - init");
    NSAssert(self, @"super init cannot be nil");
    _persistentState = [NSUserDefaults standardUserDefaults];
    _eventQueue = [[NSMutableArray alloc] init];
    _locationManager = [[CLLocationManager alloc] init];
    [_locationManager setDelegate:self];
    [_locationManager requestAlwaysAuthorization];
    if (@available(iOS 9.0, *)) {
        _locationManager.allowsBackgroundLocationUpdates = YES;
    } else {
        // Fallback on earlier versions
    }
    
    _headlessRunner = [[FlutterEngine alloc] initWithName:@"GeoLocationIsolate" project:nil allowHeadlessExecution:YES];
    _registrar = registrar;
    
    _mainChannel = [FlutterMethodChannel methodChannelWithName:@"plugins.flutter.io/geolocation_plugin"
                                               binaryMessenger:[registrar messenger]];
    [registrar addMethodCallDelegate:self channel:_mainChannel];
    
    _callbackChannel =
    [FlutterMethodChannel methodChannelWithName:@"plugins.flutter.io/geolocation_plugin_background"
                                binaryMessenger:_headlessRunner];
    return self;
}

- (void)startGeoLocationService:(int64_t)handle {
    NSLog(@"GeoLocationPlugin - startGeoLocationService");
    [self setCallbackDispatcherHandle:handle];
    FlutterCallbackInformation *info = [FlutterCallbackCache lookupCallbackInformation:handle];
    NSAssert(info != nil, @"failed to find callback");
    NSString *entrypoint = info.callbackName;
    NSString *uri = info.callbackLibraryPath;
    [_headlessRunner runWithEntrypoint:entrypoint libraryURI:uri];
    NSAssert(registerPlugins != nil, @"failed to set registerPlugins");
    
    // Once our headless runner has been started, we need to register the application's plugins
    // with the runner in order for them to work on the background isolate. `registerPlugins` is
    // a callback set from AppDelegate.m in the main application. This callback should register
    // all relevant plugins (excluding those which require UI).
    registerPlugins(_headlessRunner);
    [_registrar addMethodCallDelegate:self channel:_callbackChannel];
}

- (void)registerGeoLocation:(NSArray *)arguments {
    NSLog(@"GeoLocationPlugin - registerGeoLocation");
    int64_t callbackHandle = [arguments[0] longLongValue];
    NSString *username = arguments[1];
    NSString *deviceId = arguments[2];
    [self setLocationCallbackHandle:callbackHandle];
    [self setGeoUsername:username];
    [self setGeoDeviceId:deviceId];
    
    [self->_locationManager startMonitoringSignificantLocationChanges];
}

- (BOOL)removeGeoLocation:(NSArray *)arguments {
    NSLog(@"GeoLocationPlugin - removeGeoLocation");
    [self->_locationManager stopMonitoringSignificantLocationChanges];
    return YES;
}

- (int64_t)getCallbackDispatcherHandle {
    id handle = [_persistentState objectForKey:@"callback_dispatcher_handle"];
    if (handle == nil) {
        return 0;
    }
    return [handle longLongValue];
}

- (void)setCallbackDispatcherHandle:(int64_t)handle {
    [_persistentState setObject:[NSNumber numberWithLongLong:handle]
                         forKey:@"callback_dispatcher_handle"];
    [_persistentState synchronize];
}

- (int64_t)getLocationCallbackHandle {
    id handle = [_persistentState objectForKey:@"location_callback_handle"];
    if (handle == nil) {
        return 0;
    }
    return [handle longLongValue];
}

- (void)setLocationCallbackHandle:(int64_t)handle {
    [_persistentState setObject:[NSNumber numberWithLongLong:handle]
                         forKey:@"location_callback_handle"];
}

- (NSString*)getGeoUsername {
    id handle = [_persistentState objectForKey:@"geo_location_username"];
    if (handle == nil) {
        return nil;
    }
    return handle;
}

- (void)setGeoUsername:(NSString*)username {
    [_persistentState setObject:username
                         forKey:@"geo_location_username"];
    [_persistentState synchronize];
}

- (NSString*)getGeoDeviceId {
    id handle = [_persistentState objectForKey:@"geo_location_device_id"];
    if (handle == nil) {
        return nil;
    }
    return handle;
}

- (void)setGeoDeviceId:(NSString*)deviceId {
    [_persistentState setObject:deviceId
                         forKey:@"geo_location_device_id"];
    [_persistentState synchronize];
}

@end

