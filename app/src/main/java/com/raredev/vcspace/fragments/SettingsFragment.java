package com.raredev.vcspace.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.blankj.utilcode.util.ToastUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.raredev.vcspace.R;
import com.raredev.vcspace.databinding.LayoutCredentialBinding;
import com.raredev.vcspace.managers.SettingsManager;
import com.raredev.vcspace.util.PreferencesUtils;

public class SettingsFragment extends PreferenceFragmentCompat {

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.settings, rootKey);

    Preference github = findPreference(SettingsManager.KEY_GITHUB);
    github.setOnPreferenceClickListener(
        (pref) -> {
          String url = "https://github.com/raredeveloperofc/Visual-Code-Space";
          Intent i = new Intent(Intent.ACTION_VIEW);
          i.setData(Uri.parse(url));
          startActivity(i);
          return true;
        });
    Preference credential = findPreference(SettingsManager.KEY_CREDENTIAL);
    credential.setOnPreferenceClickListener(
        (pref) -> {
          LayoutCredentialBinding binding = LayoutCredentialBinding.inflate(getLayoutInflater());
          TextView helper = binding.helper;
          var content = helper.getText().toString();
          var linkText = getString(R.string.github_token);
          var startIndex = content.indexOf(linkText);
          var endIndex = startIndex + linkText.length();

          var spannableString = new SpannableString(content);
          spannableString.setSpan(
              new ClickableSpan() {
                @Override
                public void onClick(View textView) {
                  String url = "https://github.com/settings/tokens";
                  Intent i = new Intent(Intent.ACTION_VIEW);
                  i.setData(Uri.parse(url));
                  startActivity(i);
                }
              },
              startIndex,
              endIndex,
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

          helper.setText(spannableString);
          helper.setMovementMethod(LinkMovementMethod.getInstance());
          helper.setHighlightColor(Color.TRANSPARENT);

          var prefs = PreferencesUtils.getDefaultPrefs();
          binding.etInputUsername.setText(
              prefs.getString(SettingsManager.KEY_CREDENTIAL_USERNAME, ""));
          binding.etInputPassword.setText(
              prefs.getString(SettingsManager.KEY_CREDENTIAL_PASSWORD, ""));

          new MaterialAlertDialogBuilder(requireContext())
              .setTitle(getString(R.string.pref_git_credentials))
              .setPositiveButton(
                  getString(android.R.string.ok),
                  (d, w) -> {
                    prefs
                        .edit()
                        .putString(
                            SettingsManager.KEY_CREDENTIAL_USERNAME,
                            binding.etInputUsername.getText().toString())
                        .apply();
                    prefs
                        .edit()
                        .putString(
                            SettingsManager.KEY_CREDENTIAL_PASSWORD,
                            binding.etInputPassword.getText().toString())
                        .apply();
                  })
              .setView(binding.getRoot())
              .show();
          return true;
        });
    Preference theme = findPreference(SettingsManager.KEY_THEME);
    theme.setOnPreferenceChangeListener(
        (preference, newValue) -> {
          AppCompatDelegate.setDefaultNightMode(getTheme((String) newValue));
          return true;
        });
  }

  public static int getThemeFromPrefs() {
    SharedPreferences prefs = PreferencesUtils.getDefaultPrefs();
    String selectedTheme = prefs.getString(SettingsManager.KEY_THEME, "default");
    return getTheme(selectedTheme);
  }

  private static int getTheme(String selectedTheme) {
    switch (selectedTheme) {
      case "light":
        return AppCompatDelegate.MODE_NIGHT_NO;
      case "dark":
        return AppCompatDelegate.MODE_NIGHT_YES;
      default:
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }
  }
}
