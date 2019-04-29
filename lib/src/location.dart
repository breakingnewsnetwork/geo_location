// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Internal.
UserLocation locationFromList(List<double> l, List<String> i) => UserLocation._fromList(l, i);

/// A simple representation of a geographic location.
class UserLocation {
  final double latitude;
  final double longitude;
  final String username;
  final String deviceId;

  const UserLocation(this.latitude, this.longitude, this.username, this.deviceId);

  UserLocation._fromList(List<double> l, List<String> i)
      : assert(l.length == 2 && i.length == 2),
        latitude = l[0],
        longitude = l[1],
        username = i[0],
        deviceId = i[1];

  @override
  String toString() => '($latitude, $longitude) - $username - $deviceId';
}
