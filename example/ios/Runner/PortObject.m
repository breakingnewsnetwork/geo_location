//
//  PortObject.m
//  Runner
//
//  Created by Darius Staigys on 2019-04-28.
//  Copyright © 2019 The Chromium Authors. All rights reserved.
//

#import "PortObject.h"
#import "GeneratedPluginRegistrant.h"
#import <geo_location/GeoLocationPlugin.h>

void registerPlugins(NSObject<FlutterPluginRegistry>* registry) {
    [GeneratedPluginRegistrant registerWithRegistry:registry];
}

@implementation PortObject
+ (void)registerWithRegistry:(NSObject<FlutterPluginRegistry>*)registry {
    [GeoLocationPlugin setPluginRegistrantCallback:registerPlugins];
}
@end
