/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Log;

/**
 * Settings fragment for AppRTC.
 */
public class SettingsFragment extends PreferenceFragment {
  private static final String TAG = "SettingsFragment";
    private Preference pAboutApp;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Load the preferences from an XML resource
    addPreferencesFromResource(R.xml.webrtc_preferences);

    // Load package info
    String temp;
    try {
      PackageInfo pkg = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
      temp = pkg.versionName;
    } catch (PackageManager.NameNotFoundException e) {
      temp = "";
      Log.e(TAG, "Error while showing about dialog", e);
    }
    final String appVersion = temp;

    
        /* About App */
    pAboutApp = findPreference("about_app");
    if (pAboutApp != null) {
      pAboutApp.setTitle(String.format(getString(R.string.about_android), getString(R.string.app_name)));
      pAboutApp.setSummary(String.format(getString(R.string.about_version), appVersion));
    }

  }
}
