// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:geo_location/src/location.dart';

void callbackDispatcher() {
  const MethodChannel _backgroundChannel = MethodChannel('plugins.flutter.io/geolocation_plugin_background');
  WidgetsFlutterBinding.ensureInitialized();

  _backgroundChannel.setMethodCallHandler((MethodCall call) async {

    final List<dynamic> args = call.arguments;
    print('callback from backdround ${args[0]}');
    final Function callback = PluginUtilities.getCallbackFromHandle(CallbackHandle.fromRawHandle(args[0]));
    assert(callback != null);
    final List<double> locationList = <double>[];
    final List<String> infoList = <String>[];
    // 0.0 becomes 0 somewhere during the method call, resulting in wrong
    // runtime type (int instead of double). This is a simple way to get
    // around casting in another complicated manner.
    args[1].forEach((dynamic e) => locationList.add(double.parse(e.toString())));
    args[2].forEach((dynamic e) => infoList.add(e.toString()));
    final UserLocation triggeringLocation = locationFromList(locationList, infoList);
    callback(triggeringLocation);

  });
  _backgroundChannel.invokeMethod('LocationUpdatesService.initialized');
}
