package com.raredev.vcspace.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.PopupMenu;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.raredev.vcspace.util.FileUtil;
import com.raredev.vcspace.util.ILogger;
import com.raredev.vcspace.util.Utils;
import com.raredev.vcspace.R;
import com.raredev.vcspace.actions.Action;
import com.raredev.vcspace.actions.ActionData;
import com.raredev.vcspace.actions.ActionManager;
import com.raredev.vcspace.databinding.ActivityMainBinding;
import com.raredev.vcspace.ui.editor.CodeEditorView;
import com.raredev.vcspace.ui.editor.Symbol;
import com.raredev.vcspace.ui.editor.manager.EditorManager;
import com.raredev.vcspace.ui.viewmodel.EditorViewModel;
import com.raredev.vcspace.util.PreferencesUtils;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends VCSpaceActivity
    implements SharedPreferences.OnSharedPreferenceChangeListener,
        NavigationView.OnNavigationItemSelectedListener {
  protected final String LOG_TAG = MainActivity.class.getSimpleName();
  public ActivityMainBinding binding;

  public EditorViewModel viewModel;
  public EditorManager editorManager;

  public ActivityResultLauncher<Intent> launcher;
  public ActivityResultLauncher<String> createFile;
  public ActivityResultLauncher<String> pickFile;

  @Override
  public View getLayout() {
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    return binding.getRoot();
  }

  @Override
  public void onCreate() {
    setSupportActionBar(binding.toolbar);
    binding.navEnd.setNavigationItemSelectedListener(this);
    ActionBarDrawerToggle toggle =
        new ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar, R.string.open, R.string.close);
    binding.drawerLayout.addDrawerListener(toggle);
    toggle.syncState();

    viewModel = new ViewModelProvider(this).get(EditorViewModel.class);
    editorManager = new EditorManager(this, binding, viewModel);
    viewModel.removeAllFiles();

    binding.tabLayout.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          @Override
          public void onTabUnselected(TabLayout.Tab p1) {}

          @Override
          public void onTabReselected(TabLayout.Tab p1) {
            ActionData data = new ActionData();
            data.put(MainActivity.class, MainActivity.this);

            PopupMenu pm = new PopupMenu(MainActivity.this, p1.view);
            ActionManager.getInstance().fillMenu(pm.getMenu(), data, Action.Location.EDITOR);
            pm.show();
          }

          @Override
          public void onTabSelected(TabLayout.Tab p1) {
            int position = p1.getPosition();
            CodeEditorView editor = editorManager.getEditorAtIndex(position);
            viewModel.setCurrentFile(position, editor.getFile());

            binding.searcher.bindEditor(editor);
            refreshSymbolInput(editor);
            invalidateOptionsMenu();
          }
        });

    getLifecycle().addObserver(new LifecyclerObserver());
    ThemeRegistry.getInstance().setTheme(Utils.isDarkMode(this) ? "vcspace_dark" : "vcspace_light");
    registerResultActivity();
    observeViewModel();
  }

  @Override
  protected void onResume() {
    super.onResume();
    invalidateOptionsMenu();
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences pref, String key) {
    editorManager.onSharedPreferenceChanged(key);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    menu.clear();
    ActionData data = new ActionData();
    data.put(MainActivity.class, this);
    data.put(EditorManager.class, editorManager);

    ActionManager.getInstance().fillMenu(menu, data, Action.Location.MAIN_TOOLBAR);
    return true;
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    if (menu instanceof MenuBuilder) {
      ((MenuBuilder) menu).setOptionalIconsVisible(true);
    }
    return true;
  }

  @Override
  public boolean onNavigationItemSelected(MenuItem item) {
    int id = item.getItemId();
    switch (id) {
      case R.id.menu_viewlogs:
        editorManager.saveAllFiles(false);
        startActivity(new Intent(getApplicationContext(), LogViewActivity.class));
        break;
      case R.id.menu_settings:
        editorManager.saveAllFiles(false);
        startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
        break;
    }
    binding.drawerLayout.closeDrawers();
    return true;
  }

  @Override
  public void onBackPressed() {
    if (binding.drawerLayout.isOpen()) {
      binding.drawerLayout.closeDrawers();
      return;
    }
    if (binding.searcher.isShowing) {
      binding.searcher.showAndHide();
      return;
    }
    editorManager.saveAllFiles(false);
    super.onBackPressed();
  }

  private void registerResultActivity() {
    launcher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == RESULT_OK) {
                Uri uri = result.getData().getData();
                try {
                  OutputStream outputStream = getContentResolver().openOutputStream(uri);
                  outputStream.write(
                      editorManager.getCurrentEditor().getText().toString().getBytes());
                  outputStream.close();
                  editorManager.openFile(FileUtil.getFileFromUri(MainActivity.this, uri));
                } catch (IOException e) {
                  ILogger.error(LOG_TAG, Log.getStackTraceString(e));
                }
              }
            });
    createFile =
        registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/*"),
            uri -> {
              if (uri != null) {
                try {
                  editorManager.openFile(FileUtil.getFileFromUri(this, uri));
                } catch (IOException e) {
                  ILogger.error(LOG_TAG, Log.getStackTraceString(e));
                }
              }
            });
    pickFile =
        registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
              if (uri != null) {
                try {
                  editorManager.openFile(FileUtil.getFileFromUri(this, uri));
                } catch (IOException e) {
                  ILogger.error(LOG_TAG, Log.getStackTraceString(e));
                }
              }
            });
  }

  private void observeViewModel() {
    viewModel.observeFiles(
        this,
        files -> {
          if (files.isEmpty()) {
            PreferencesUtils.getDefaultPrefs().unregisterOnSharedPreferenceChangeListener(this);
            binding.tabLayout.setVisibility(View.GONE);
            binding.layout.setVisibility(View.GONE);
            binding.noFileOpened.setVisibility(View.VISIBLE);
            binding.searcher.hide();
          } else {
            PreferencesUtils.getDefaultPrefs().registerOnSharedPreferenceChangeListener(this);
            binding.tabLayout.setVisibility(View.VISIBLE);
            binding.layout.setVisibility(View.VISIBLE);
            binding.noFileOpened.setVisibility(View.GONE);
          }
        });

    viewModel.getDisplayedFile().observe(this, index -> binding.container.setDisplayedChild(index));
  }

  private void refreshSymbolInput(CodeEditor editor) {
    binding.symbolInput.setSymbols(Symbol.baseSymbols());
    binding.symbolInput.bindEditor(editor);
  }

  private void saveAs(File fileToSave) {
    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("text/*");
    intent.putExtra(Intent.EXTRA_TITLE, fileToSave.getName());

    launcher.launch(intent);
  }
}
