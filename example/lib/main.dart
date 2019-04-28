// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:async';
import 'dart:isolate';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:geo_location/geo_location.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String geofenceState = 'N/A';
  double latitude = 37.419851;
  double longitude = -122.078818;
  double radius = 150.0;
  ReceivePort port = ReceivePort();

  @override
  void initState() {
    super.initState();
    IsolateNameServer.registerPortWithName(port.sendPort, 'geolocation_send_port');
    port.listen((dynamic data) {
      print('Event: $data');
      setState(() {
        geofenceState = data.toString();
      });
    });
    initPlatformState();
  }

  static void callback(Location l) async {
    print('Location $l');
    final SendPort send = IsolateNameServer.lookupPortByName('geolocation_send_port');
    send?.send(l);
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    print('Initializing...');
    await GeoLocationManager.initialize();
    print('Initialization done');
  }

  String numberValidator(String value) {
    if (value == null) {
      return null;
    }
    final num a = num.tryParse(value);
    if (a == null) {
      return '"$value" is not a valid number';
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
          appBar: AppBar(
            title: const Text('Flutter GeoLocation Example'),
          ),
          body: Container(
              padding: const EdgeInsets.all(20.0),
              child: Column(mainAxisAlignment: MainAxisAlignment.center, children: <Widget>[
                Text('Current state: $geofenceState'),
                Center(
                  child: RaisedButton(
                    child: const Text('Register'),
                    onPressed: () {
                      if (latitude == null) {
                        setState(() => latitude = 0.0);
                      }
                      if (longitude == null) {
                        setState(() => longitude = 0.0);
                      }
                      if (radius == null) {
                        setState(() => radius = 0.0);
                      }
                      GeoLocationManager.registerGeoLocation(callback);
                    },
                  ),
                ),
                Center(
                  child: RaisedButton(child: const Text('Unregister'), onPressed: () => GeoLocationManager.removeGeoLocation()),
                ),
                TextField(
                  decoration: const InputDecoration(
                    hintText: 'Latitude',
                  ),
                  keyboardType: TextInputType.number,
                  controller: TextEditingController(text: latitude.toString()),
                  onChanged: (String s) {
                    latitude = double.tryParse(s);
                  },
                ),
                TextField(
                    decoration: const InputDecoration(hintText: 'Longitude'),
                    keyboardType: TextInputType.number,
                    controller: TextEditingController(text: longitude.toString()),
                    onChanged: (String s) {
                      longitude = double.tryParse(s);
                    }),
                TextField(
                    decoration: const InputDecoration(hintText: 'Radius'),
                    keyboardType: TextInputType.number,
                    controller: TextEditingController(text: radius.toString()),
                    onChanged: (String s) {
                      radius = double.tryParse(s);
                    }),
              ]))),
    );
  }
}
