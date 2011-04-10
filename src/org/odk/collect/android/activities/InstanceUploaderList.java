/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.activities;

import java.util.ArrayList;

import org.odk.collect.android.R;
import org.odk.collect.android.preferences.ServerPreferences;
import org.odk.collect.android.provider.SubmissionsStorage;
import org.odk.collect.android.utilities.FilterUtils;

import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

/**
 * Responsible for displaying all the valid forms in the forms directory. Stores the path to
 * selected form for use by {@link MainMenuActivity}.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */

// TODO long click form for submission log
public class InstanceUploaderList extends ListActivity {

    private static final String BUNDLE_SELECTED_ITEMS_KEY = "selected_items";
    private static final String BUNDLE_TOGGLED_KEY = "toggled";

    private static final int MENU_PREFERENCES = Menu.FIRST;
    private static final int INSTANCE_UPLOADER = 0;

    private Button mActionButton;
    private Button mToggleButton;

    private SimpleCursorAdapter mInstances;
    private ArrayList<Long> mSelected = new ArrayList<Long>();
    private boolean mRestored = false;
    private boolean mToggled = false;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.instance_uploader_list);

        mActionButton = (Button) findViewById(R.id.upload_button);
        mActionButton.setOnClickListener(new OnClickListener() {

            @Override
			public void onClick(View arg0) {
                if (mSelected.size() > 0) {
                    // items selected
                    uploadSelectedFiles();
                    refreshData();
                    mToggled = false;
                } else {
                    // no items selected
                    Toast.makeText(getApplicationContext(), getString(R.string.noselect_error),
                        Toast.LENGTH_SHORT).show();
                }
            }

        });

        mToggleButton = (Button) findViewById(R.id.toggle_button);
        mToggleButton.setOnClickListener(new OnClickListener() {
            @Override
			public void onClick(View v) {
                // toggle selections of items to all or none
                ListView ls = getListView();
                mToggled = !mToggled;
                // remove all items from selected list
                mSelected.clear();
                for (int pos = 0; pos < ls.getCount(); pos++) {
                    ls.setItemChecked(pos, mToggled);
                    // add all items if mToggled sets to select all
                    if (mToggled)
                        mSelected.add(ls.getItemIdAtPosition(pos));
                }
                mActionButton.setEnabled(!(mSelected.size() == 0));

            }
        });

    }


    /**
     * Retrieves instance information from {@link SubmissionsStorage}, composes and displays each row.
     */
    private void refreshView() {
    	String[] projection = new String[] {
    			SubmissionsStorage.KEY_ID,
    			SubmissionsStorage.KEY_DISPLAY_NAME,
    			SubmissionsStorage.KEY_DISPLAY_SUBTEXT
    	};
        String[] data = new String[] {
        		SubmissionsStorage.KEY_DISPLAY_NAME,
        		SubmissionsStorage.KEY_DISPLAY_SUBTEXT
        };
        int[] view = new int[] {
                R.id.text1, R.id.text2
        };
        String sortOrder = SubmissionsStorage.KEY_DISPLAY_NAME + " ASC";

        FilterUtils.FilterCriteria fd =
    		FilterUtils.buildSelectionClause(SubmissionsStorage.KEY_STATUS, SubmissionsStorage.STATUS_COMPLETE);

        Cursor c = getContentResolver().query(SubmissionsStorage.CONTENT_URI_INFO_DATASET, 
        		projection, fd.selection, fd.selectionArgs, sortOrder );
        startManagingCursor(c);

        // render total instance view
        mInstances =
            new SimpleCursorAdapter(this, R.layout.two_item_multiple_choice, c, data, view);
        setListAdapter(mInstances);
        getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        getListView().setItemsCanFocus(false);
        mActionButton.setEnabled(!(mSelected.size() == 0));

        // set title
        setTitle(getString(R.string.app_name) + " > " + getString(R.string.send_data));

        // if current activity is being reinitialized due to changing orientation restore all check
        // marks for ones selected
        if (mRestored) {
            ListView ls = getListView();
            for (long id : mSelected) {
                for (int pos = 0; pos < ls.getCount(); pos++) {
                    if (id == ls.getItemIdAtPosition(pos)) {
                        ls.setItemChecked(pos, true);
                        break;
                    }
                }

            }
            mRestored = false;
        }
    }


    private void uploadSelectedFiles() {
        ArrayList<String> selectedInstances = new ArrayList<String>();

        for (int i = 0; i < mSelected.size(); i++) {
        	Cursor c = null;
        	try {
        		c = getContentResolver().query(
        			ContentUris.withAppendedId(SubmissionsStorage.CONTENT_URI_INFO_DATASET, mSelected.get(i)),
        				new String[] { SubmissionsStorage.KEY_ID, SubmissionsStorage.KEY_INSTANCE_FILE_PATH }, null, null, null);
        		if ( c.moveToNext() ) {
        			String s = c.getString(c.getColumnIndex(SubmissionsStorage.KEY_INSTANCE_FILE_PATH));
        			selectedInstances.add(s);
        		}
        	} finally {
        		if ( c != null ) {
        			c.close();
        		}
        	}
        }

        // bundle intent with upload files
        Intent i = new Intent(this, InstanceUploaderActivity.class);
        i.putExtra(FormEntryActivity.KEY_INSTANCES, selectedInstances);
        startActivityForResult(i, INSTANCE_UPLOADER);
    }


    private void refreshData() {
        if (!mRestored) {
            mSelected.clear();
        }
        refreshView();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_PREFERENCES, 0, getString(R.string.server_preferences)).setIcon(
            android.R.drawable.ic_menu_preferences);
        return true;
    }


    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case MENU_PREFERENCES:
                createPreferencesMenu();
                return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }


    private void createPreferencesMenu() {
        Intent i = new Intent(this, ServerPreferences.class);
        startActivity(i);
    }


    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        // get row id from db
        Cursor c = (Cursor) getListAdapter().getItem(position);
        long k = c.getLong(c.getColumnIndex(SubmissionsStorage.KEY_ID));

        // add/remove from selected list
        if (mSelected.contains(k))
            mSelected.remove(k);
        else
            mSelected.add(k);

        mActionButton.setEnabled(!(mSelected.size() == 0));

    }


	@Override
    protected void onResume() {
        refreshData();
        super.onResume();
    }


    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        long[] selectedArray = savedInstanceState.getLongArray(BUNDLE_SELECTED_ITEMS_KEY);
        for (int i = 0; i < selectedArray.length; i++)
            mSelected.add(selectedArray[i]);
        mToggled = savedInstanceState.getBoolean(BUNDLE_TOGGLED_KEY);
        mRestored = true;
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        long[] selectedArray = new long[mSelected.size()];
        for (int i = 0; i < mSelected.size(); i++)
            selectedArray[i] = mSelected.get(i);
        outState.putLongArray(BUNDLE_SELECTED_ITEMS_KEY, selectedArray);
        outState.putBoolean(BUNDLE_TOGGLED_KEY, mToggled);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == RESULT_CANCELED) {
            return;
        }
        switch (requestCode) {
            // returns with a form path, start entry
            case INSTANCE_UPLOADER:
                if (intent.getBooleanExtra(FormEntryActivity.KEY_SUCCESS, false)) {
                    refreshData();
                    if (mInstances.isEmpty()) {
                        finish();
                    }
                }
                break;
            default:
                break;
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

}
