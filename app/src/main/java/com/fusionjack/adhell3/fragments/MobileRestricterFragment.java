package com.fusionjack.adhell3.fragments;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.Toast;

import com.fusionjack.adhell3.App;
import com.fusionjack.adhell3.R;
import com.fusionjack.adhell3.adapter.AppInfoAdapter;
import com.fusionjack.adhell3.db.AppDatabase;
import com.fusionjack.adhell3.db.entity.AppInfo;
import com.fusionjack.adhell3.db.entity.RestrictedPackage;
import com.fusionjack.adhell3.utils.AdhellAppIntegrity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.util.List;

import javax.inject.Inject;

import static com.fusionjack.adhell3.fragments.LoadAppAsyncTask.SORTED_RESTRICTED;
import static com.fusionjack.adhell3.fragments.LoadAppAsyncTask.SORTED_RESTRICTED_ALPHABETICALLY;
import static com.fusionjack.adhell3.fragments.LoadAppAsyncTask.SORTED_RESTRICTED_INSTALL_TIME;

public class MobileRestricterFragment extends Fragment {

    @Inject
    AppDatabase appDatabase;
    @Inject
    PackageManager packageManager;

    private Context context;
    private int sortState = SORTED_RESTRICTED_ALPHABETICALLY;
    private int layout;

    public MobileRestricterFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.get().getAppComponent().inject(this);
        context = getContext();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getActivity().setTitle(getString(R.string.mobile_restricter_fragment_title));
        AppCompatActivity parentActivity = (AppCompatActivity) getActivity();
        if (parentActivity.getSupportActionBar() != null) {
            parentActivity.getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            parentActivity.getSupportActionBar().setHomeButtonEnabled(false);
        }
        setHasOptionsMenu(true);

        layout = R.id.enabled_apps_list;

        View view = inflater.inflate(R.layout.fragment_mobile_restricter, container, false);

        ListView installedAppsView = view.findViewById(layout);
        installedAppsView.setOnItemClickListener((AdapterView<?> adView, View view2, int position, long id) -> {
            AppInfoAdapter adapter = (AppInfoAdapter) adView.getAdapter();
            String packageName = adapter.getItem(position).packageName;
            new SetAppAsyncTask(packageName, view2, appDatabase).execute();
        });

        SwipeRefreshLayout swipeContainer = view.findViewById(R.id.swipeContainer);
        swipeContainer.setOnRefreshListener(() ->
                new RefreshAppAsyncTask(sortState, layout, false, context, appDatabase, packageManager).execute()
        );

        new LoadAppAsyncTask("", sortState, layout, false, context, appDatabase, packageManager).execute();
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.mobile_restricter_menu, menu);

        SearchView searchView = (SearchView) menu.findItem(R.id.search).getActionView();
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String text) {
                new LoadAppAsyncTask(text, sortState, layout, false, context, appDatabase, packageManager).execute();
                return false;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_pack_dis_sort:
                break;
            case R.id.sort_alphabetically_item:
                if (sortState == SORTED_RESTRICTED_ALPHABETICALLY) break;
                sortState = SORTED_RESTRICTED_ALPHABETICALLY;
                Toast.makeText(context, getString(R.string.app_list_sorted_by_alphabet), Toast.LENGTH_SHORT).show();
                new LoadAppAsyncTask("", sortState, layout, false, context, appDatabase, packageManager).execute();
                break;
            case R.id.sort_by_time_item:
                if (sortState == SORTED_RESTRICTED_INSTALL_TIME) break;
                sortState = SORTED_RESTRICTED_INSTALL_TIME;
                Toast.makeText(context, getString(R.string.app_list_sorted_by_date), Toast.LENGTH_SHORT).show();
                new LoadAppAsyncTask("", sortState, layout, false, context, appDatabase, packageManager).execute();
                break;
            case R.id.sort_restricted_item:
                if (sortState == SORTED_RESTRICTED) break;
                sortState = SORTED_RESTRICTED;
                Toast.makeText(context, getString(R.string.app_list_sorted_by_restricted), Toast.LENGTH_SHORT).show();
                new LoadAppAsyncTask("", sortState, layout, false, context, appDatabase, packageManager).execute();
                break;
            case R.id.restricter_import_storage:
                Toast.makeText(context, getString(R.string.imported_restricted_from_storage), Toast.LENGTH_SHORT).show();
                importList();
                break;
            case R.id.restricter_export_storage:
                Toast.makeText(context, getString(R.string.exported_restricted_to_storage), Toast.LENGTH_SHORT).show();
                exportList();
                break;
            case R.id.restricter_enable_all:
                Toast.makeText(context, getString(R.string.enabled_all_restricted), Toast.LENGTH_SHORT).show();
                enableAllPackages();
        }
        return super.onOptionsItemSelected(item);
    }

    private void importList() {
        AsyncTask.execute(() -> {
            File file = new File(Environment.getExternalStorageDirectory(), "mobile_restricted_packages.txt");
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                appDatabase.restrictedPackageDao().deleteAll();
                String line;
                while ((line = reader.readLine()) != null) {
                    AppInfo appInfo = appDatabase.applicationInfoDao().getByPackageName(line);
                    appInfo.mobileRestricted = true;
                    appDatabase.applicationInfoDao().insert(appInfo);

                    RestrictedPackage restrictedPackage = new RestrictedPackage();
                    restrictedPackage.packageName = line;
                    restrictedPackage.policyPackageId = AdhellAppIntegrity.DEFAULT_POLICY_ID;
                    appDatabase.restrictedPackageDao().insert(restrictedPackage);
                }
                new LoadAppAsyncTask("", sortState, layout, false, context, appDatabase, packageManager).execute();
            } catch (IOException e) {
                Log.e("Exception", "File write failed: " + e.toString());
            }
        });
    }

    private void exportList() {
        AsyncTask.execute(() -> {
            File file = new File(Environment.getExternalStorageDirectory(), "mobile_restricted_packages.txt");
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file))) {
                writer.write("");
                List<AppInfo> restrictedAppList = appDatabase.applicationInfoDao().getMobileRestrictedApps();
                for (AppInfo app : restrictedAppList) {
                    writer.append(app.packageName);
                    writer.append("\n");
                }
            } catch (IOException e) {
                Log.e("Exception", "File write failed: " + e.toString());
            }
        });
    }

    private void enableAllPackages() {
        AsyncTask.execute(() -> {
            List<AppInfo> restrictedAppList = appDatabase.applicationInfoDao().getMobileRestrictedApps();
            for (AppInfo app : restrictedAppList) {
                app.mobileRestricted = false;
                appDatabase.applicationInfoDao().insert(app);
            }
            appDatabase.restrictedPackageDao().deleteAll();
            new LoadAppAsyncTask("", sortState, layout, false, context, appDatabase, packageManager).execute();
        });
    }

    private static class SetAppAsyncTask extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<View> viewWeakReference;
        private AppDatabase appDatabase;
        private String packageName;

        SetAppAsyncTask(String packageName, View view, AppDatabase appDatabase) {
            this.viewWeakReference = new WeakReference<>(view);
            this.packageName = packageName;
            this.appDatabase = appDatabase;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            AppInfo appInfo = appDatabase.applicationInfoDao().getByPackageName(packageName);
            appInfo.mobileRestricted = !appInfo.mobileRestricted;
            if (appInfo.mobileRestricted) {
                RestrictedPackage restrictedPackage = new RestrictedPackage();
                restrictedPackage.packageName = packageName;
                restrictedPackage.policyPackageId = AdhellAppIntegrity.DEFAULT_POLICY_ID;
                appDatabase.restrictedPackageDao().insert(restrictedPackage);
            } else {
                appDatabase.restrictedPackageDao().deleteByPackageName(packageName);
            }
            appDatabase.applicationInfoDao().insert(appInfo);
            return appInfo.mobileRestricted;
        }

        @Override
        protected void onPostExecute(Boolean state) {
            ((Switch) viewWeakReference.get().findViewById(R.id.switchDisable)).setChecked(!state);
        }
    }
}
