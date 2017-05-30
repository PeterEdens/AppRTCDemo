package org.appspot.apprtc;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.NavigationView;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.appspot.apprtc.util.ThumbnailsCacheManager;

import java.io.ByteArrayOutputStream;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;


/**
 * Base class to handle setup of the drawer implementation including user switching and avatar fetching and fallback
 * generation.
 */
public abstract class DrawerActivity extends AppCompatActivity {
    private static final String TAG = DrawerActivity.class.getSimpleName();
    private static final String KEY_IS_ACCOUNT_CHOOSER_ACTIVE = "IS_ACCOUNT_CHOOSER_ACTIVE";
    private static final String KEY_CHECKED_MENU_ITEM = "CHECKED_MENU_ITEM";
    private static final int ACTION_MANAGE_ACCOUNTS = 101;
    private static final int MENU_ORDER_ACCOUNT = 1;
    private static final int MENU_ORDER_ACCOUNT_FUNCTION = 2;

    /**
     * Flag to signal that the activity will is finishing to enforce the creation of an ownCloud {@link Account}.
     */
    private boolean mRedirectingToSetupAccount = false;

    /**
     * Flag to signal when the value of mAccount was set.
     */
    protected boolean mAccountWasSet;

    /**
     * Flag to signal when the value of mAccount was restored from a saved state.
     */
    protected boolean mAccountWasRestored;

    /**
     * menu account avatar radius.
     */
    protected float mMenuAccountAvatarRadiusDimension;

    /**
     * current account avatar radius.
     */
    private float mCurrentAccountAvatarRadiusDimension;

    /**
     * other accounts avatar radius.
     */
    private float mOtherAccountAvatarRadiusDimension;

    /**
     * Reference to the drawer layout.
     */
    private DrawerLayout mDrawerLayout;

    /**
     * Reference to the drawer toggle.
     */
    private ActionBarDrawerToggle mDrawerToggle;

    /**
     * Reference to the navigation view.
     */
    private NavigationView mNavigationView;

    /**
     * Reference to the account chooser toggle.
     */
    private ImageView mAccountChooserToggle;

    /**
     * Reference to the middle account avatar.
     */
    private ImageView mAccountMiddleAccountAvatar;

    /**
     * Reference to the end account avatar.
     */
    private ImageView mAccountEndAccountAvatar;

    /**
     * Flag to signal if the account chooser is active.
     */
    private boolean mIsAccountChooserActive;

    /**
     * Id of the checked menu item.
     */
    private int mCheckedMenuItem = Menu.NONE;

    /**
     * accounts for the (max) three displayed accounts in the drawer header.
     */
    private Account[] mAvatars = new Account[3];

    /**
     * container layout of the quota view.
     */
    private LinearLayout mQuotaView;

    /**
     * progress bar of the quota view.
     */
    private ProgressBar mQuotaProgressBar;

    /**
     * text view of the quota view.
     */
    private TextView mQuotaTextView;
    private Account mCurrentAccount;

    public String getStatusText() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        return pref.getString(getString(R.string.pref_status_key), "");
    }

    /**
     * Initializes the drawer, its content and highlights the menu item with the given id.
     * This method needs to be called after the content view has been set.
     *
     * @param menuItemId the menu item to be checked/highlighted
     */
    protected void setupDrawer(int menuItemId) {
        setupDrawer();
        setDrawerMenuItemChecked(menuItemId);
    }

    /**
     * Initializes the drawer and its content.
     * This method needs to be called after the content view has been set.
     */
    protected void setupDrawer() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        mNavigationView = (NavigationView) findViewById(R.id.nav_view);
        if (mNavigationView != null) {
            setupDrawerHeader();

            setupDrawerMenu(mNavigationView);

            setupQuotaElement();
        }

        setupDrawerToggle();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    /**
     * initializes and sets up the drawer toggle.
     */
    private void setupDrawerToggle() {
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                // standard behavior of drawer is to switch to the standard menu on closing

                invalidateOptionsMenu();
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                mDrawerToggle.setDrawerIndicatorEnabled(true);
                invalidateOptionsMenu();
            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerToggle.setDrawerIndicatorEnabled(true);
    }

    /**
     * initializes and sets up the drawer header.
     */
    private void setupDrawerHeader() {
        mAccountChooserToggle = (ImageView) findNavigationViewChildById(R.id.drawer_account_chooser_toogle);
        mAccountChooserToggle.setImageResource(R.drawable.ic_down);
        mIsAccountChooserActive = false;
        mAccountMiddleAccountAvatar = (ImageView) findNavigationViewChildById(R.id.drawer_account_middle);
        mAccountEndAccountAvatar = (ImageView) findNavigationViewChildById(R.id.drawer_account_end);

        Account current = getCurrentOwnCloudAccount(this);
        if (current != null) {
            mCurrentAccount = current;
        }

        findNavigationViewChildById(R.id.drawer_active_user)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleAccountList();
                    }
                });



    }

    /**
     * setup quota elements of the drawer.
     */
    private void setupQuotaElement() {

    }

    /**
     * setup drawer content, basically setting the item selected listener.
     *
     * @param navigationView the drawers navigation view
     */
    protected void setupDrawerMenu(NavigationView navigationView) {
        // on pre lollipop the light theme adds a black tint to icons with white coloring
        // ruining the generic avatars, so tinting for icons is deactivated pre lollipop
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            //navigationView.setItemIconTintList(null);
        }

        // setup actions for drawer menu items
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {

                        mDrawerLayout.closeDrawers();

                        if (menuItem.getItemId() == R.id.nav_chat) {
                            String className = getString(R.string.chooser_class);

                            if (className.length() != 0) {
                                Class<?> c = null;
                                try {
                                    c = Class.forName(className);
                                }
                                catch (ClassNotFoundException e) {
                                    e.printStackTrace();
                                }

                                if (c != null) {
                                    Intent chatIntent = new Intent(getApplicationContext(),
                                            c);
                                    chatIntent.putExtra(getString(R.string.extra_mode), getString(R.string.mode_im));
                                    chatIntent.addFlags(FLAG_ACTIVITY_CLEAR_TOP);
                                    startActivity(chatIntent);
                                }
                            }
                        }
                        else if (menuItem.getItemId() == R.id.nav_share_files) {
                            String className = getString(R.string.chooser_class);

                            if (className.length() != 0) {
                                Class<?> c = null;
                                try {
                                    c = Class.forName(className);
                                }
                                catch (ClassNotFoundException e) {
                                    e.printStackTrace();
                                }

                                if (c != null) {
                                    Intent chatIntent = new Intent(getApplicationContext(),
                                            c);
                                    chatIntent.putExtra(getString(R.string.extra_mode), getString(R.string.mode_share_files));
                                    chatIntent.addFlags(FLAG_ACTIVITY_CLEAR_TOP);
                                    startActivity(chatIntent);
                                }
                            }
                        }
                        else if (menuItem.getItemId() == R.id.action_settings) {
                            startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
                        }
                        else if (menuItem.getItemId() == R.id.drawer_menu_account_manage) {
                            Intent intent = new Intent(getApplicationContext(), getActivityClass(R.string.manage_accounts_class));
                            startActivity(intent);
                        }
                        else if (menuItem.getItemId() == R.id.drawer_menu_account_add) {
                            createAccount(false);
                        }
                        else if (menuItem.getItemId() == Menu.NONE) {
                            // account clicked
                            accountClicked(menuItem.getTitle().toString());
                        }


                        return true;
                    }
                });


    }

    /**
     * Tries to swap the current ownCloud {@link Account} for other valid and existing.
     *
     * If no valid ownCloud {@link Account} exists, the the user is requested
     * to create a new ownCloud {@link Account}.
     *
     * POSTCONDITION: updates {@link #mAccountWasSet} and {@link #mAccountWasRestored}.
     */
    protected void swapToDefaultAccount() {
        // default to the most recently used account
        Account newAccount = getCurrentOwnCloudAccount(getApplicationContext());
        if (newAccount == null) {
            /// no account available: force account creation
            createAccount(true);
            mRedirectingToSetupAccount = true;
            mAccountWasSet = false;
            mAccountWasRestored = false;

        } else {
            mAccountWasSet = true;
            mAccountWasRestored = (newAccount.equals(mCurrentAccount));
            mCurrentAccount = newAccount;
        }
    }

    /**
     * Sets and validates the ownCloud {@link Account} associated to the Activity.
     *
     * If not valid, tries to swap it for other valid and existing ownCloud {@link Account}.
     *
     * POSTCONDITION: updates {@link #mAccountWasSet} and {@link #mAccountWasRestored}.
     *
     * @param account      New {@link Account} to set.
     * @param savedAccount When 'true', account was retrieved from a saved instance state.
     */
    protected void setAccount(Account account, boolean savedAccount) {
        Account oldAccount = mCurrentAccount;
        boolean validAccount =
                (account != null && setCurrentOwnCloudAccount(getApplicationContext(),
                        account.name));
        if (validAccount) {
            mCurrentAccount = account;
            mAccountWasSet = true;
            mAccountWasRestored = (savedAccount || mCurrentAccount.equals(oldAccount));

        } else {
            swapToDefaultAccount();
        }
    }


    /**
     * Helper class handling a callback from the {@link AccountManager} after the creation of
     * a new ownCloud {@link Account} finished, successfully or not.
     */
    public class AccountCreationCallback implements AccountManagerCallback<Bundle> {

        boolean mMandatoryCreation;

        /**
         * Constuctor
         *
         * @param mandatoryCreation     When 'true', if an account was not created, the app is closed.
         */
        public AccountCreationCallback(boolean mandatoryCreation) {
            mMandatoryCreation = mandatoryCreation;
        }

        @Override
        public void run(AccountManagerFuture<Bundle> future) {
            DrawerActivity.this.mRedirectingToSetupAccount = false;
            boolean accountWasSet = false;
            if (future != null) {
                try {
                    Bundle result;
                    result = future.getResult();
                    String name = result.getString(AccountManager.KEY_ACCOUNT_NAME);
                    String type = result.getString(AccountManager.KEY_ACCOUNT_TYPE);
                    if (setCurrentOwnCloudAccount(getApplicationContext(), name)) {
                        setAccount(new Account(name, type), false);
                        accountWasSet = true;
                    }

                    onAccountCreationSuccessful(future);
                } catch (OperationCanceledException e) {
                    Log.d(TAG, "Account creation canceled");

                } catch (Exception e) {
                    e.printStackTrace();
                }

            } else {
                Log.e(TAG, "Account creation callback with null bundle");
            }
            if (mMandatoryCreation && !accountWasSet) {
                moveTaskToBack(true);
            }
        }
    }

    protected void onAccountCreationSuccessful(AccountManagerFuture<Bundle> future) {
        updateAccountList();
        restart();
    }

    /**
     * Launches the account creation activity.
     *
     * @param mandatoryCreation     When 'true', if an account is not created by the user, the app will be closed.
     *                              To use when no ownCloud account is available.
     */
    protected void createAccount(boolean mandatoryCreation) {
        AccountManager am = AccountManager.get(getApplicationContext());
        am.addAccount(getResources().getString(R.string.account_type),
                null,
                null,
                null,
                this,
                new AccountCreationCallback(mandatoryCreation),
                new Handler());
    }


    private Class<?> getActivityClass(int classStringId) {
        String className = getString(classStringId);

        if (className.length() != 0) {
            Class<?> c = null;
            try {
                c = Class.forName(className);
            }
            catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

            return c;
        }
        return null;
    }

    /**
     * click method for mini avatars in drawer header.
     *
     * @param view the clicked ImageView
     */
    public void onAccountDrawerClick(View view) {
        accountClicked(view.getContentDescription().toString());
    }

    /**
     * sets the new/current account and restarts. In case the given account equals the actual/current account the
     * call will be ignored.
     *
     * @param accountName The account name to be set
     */
    private void accountClicked(String accountName) {
        if (!getCurrentOwnCloudAccount(getApplicationContext()).name.equals(accountName)) {
            setCurrentOwnCloudAccount(getApplicationContext(), accountName);
            restart();
        }
    }

    /**
     * restart helper method which is called after a changing the current account.
     */
    protected abstract void restart();

    /**
     * checks if the drawer exists and is opened.
     *
     * @return <code>true</code> if the drawer is open, else <code>false</code>
     */
    public boolean isDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START);
    }

    /**
     * closes the drawer.
     */
    public void closeDrawer() {
        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    /**
     * opens the drawer.
     */
    public void openDrawer() {
        if (mDrawerLayout != null) {
            mDrawerLayout.openDrawer(GravityCompat.START);
        }
    }

    /**
     * Enable or disable interaction with all drawers.
     *
     * @param lockMode The new lock mode for the given drawer. One of {@link DrawerLayout#LOCK_MODE_UNLOCKED},
     *                 {@link DrawerLayout#LOCK_MODE_LOCKED_CLOSED} or {@link DrawerLayout#LOCK_MODE_LOCKED_OPEN}.
     */
    public void setDrawerLockMode(int lockMode) {
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerLockMode(lockMode);
        }
    }

    /**
     * Enable or disable the drawer indicator.
     *
     * @param enable <code>true</code> to enable, <code>false</code> to disable
     */
    public void setDrawerIndicatorEnabled(boolean enable) {
        if (mDrawerToggle != null) {
            mDrawerToggle.setDrawerIndicatorEnabled(enable);
        }
    }

    /**
     * updates the account list in the drawer.
     */
    public void updateAccountList() {
        Account[] accounts = AccountManager.get(this).getAccountsByType(getResources().getString(R.string.account_type));
        if (mNavigationView != null && mDrawerLayout != null) {
            if (accounts.length > 0) {
                repopulateAccountList(accounts);
                setAccountInDrawer(getCurrentOwnCloudAccount(this));
                populateDrawerOwnCloudAccounts();
                int size = (int) (mOtherAccountAvatarRadiusDimension * 2);

                // activate second/end account avatar
                if (mAvatars[1] != null) {
                    setAvatar(mAvatars[1], this,
                            size, getResources(),
                            (ImageView)findNavigationViewChildById(R.id.drawer_account_end), false);
                    mAccountEndAccountAvatar.setVisibility(View.VISIBLE);
                } else {
                    mAccountEndAccountAvatar.setVisibility(View.GONE);
                }

                // activate third/middle account avatar
                if (mAvatars[2] != null) {
                    setAvatar(mAvatars[2], this,
                            size, getResources(),
                            (ImageView)findNavigationViewChildById(R.id.drawer_account_middle), false);
                    mAccountMiddleAccountAvatar.setVisibility(View.VISIBLE);
                } else {
                    mAccountMiddleAccountAvatar.setVisibility(View.GONE);
                }
            } else {
                mAccountEndAccountAvatar.setVisibility(View.GONE);
                mAccountMiddleAccountAvatar.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Getter for the ownCloud {@link Account} where the main  handled by the activity
     * is located.
     *
     * @return OwnCloud {@link Account} where the main handled by the activity
     * is located.
     */
    public Account getAccount() {
        return mCurrentAccount;
    }

    /**
     * re-populates the account list.
     *
     * @param accounts list of accounts
     */
    private void repopulateAccountList(Account[] accounts) {
// remove all accounts from list
        mNavigationView.getMenu().removeGroup(R.id.drawer_menu_accounts);

        // add all accounts to list
        for (int i = 0; i < accounts.length; i++) {
            try {
                // show all accounts except the currently active one
                if (!getAccount().name.equals(accounts[i].name)) {
                    MenuItem accountMenuItem = mNavigationView.getMenu().add(
                            R.id.drawer_menu_accounts,
                            Menu.NONE,
                            MENU_ORDER_ACCOUNT,
                            accounts[i].name)
                            .setIcon(R.drawable.user_icon_round);
                    int size = (int)mMenuAccountAvatarRadiusDimension * 2;
                    setAvatar(accounts[i], this, size, getResources(), accountMenuItem, true);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error calculating RGB value for account menu item.", e);
                mNavigationView.getMenu().add(
                        R.id.drawer_menu_accounts,
                        Menu.NONE,
                        MENU_ORDER_ACCOUNT,
                        accounts[i].name)
                        .setIcon(R.drawable.user_icon_round);
            }
        }

        // re-add add-account and manage-accounts
        mNavigationView.getMenu().add(R.id.drawer_menu_accounts, R.id.drawer_menu_account_add,
                MENU_ORDER_ACCOUNT_FUNCTION,
                getResources().getString(R.string.prefs_add_account)).setIcon(R.drawable.ic_account_plus);
        mNavigationView.getMenu().add(R.id.drawer_menu_accounts, R.id.drawer_menu_account_manage,
                MENU_ORDER_ACCOUNT_FUNCTION,
                getResources().getString(R.string.drawer_manage_accounts)).setIcon(R.drawable.ic_settings);

        // adding sets menu group back to visible, so safety check and setting invisible
        showMenu();
    }

    private void setAvatar(Account account, Context context, int size, Resources resources, MenuItem accountMenuItem, boolean fromCache) {
        AccountManager ama = AccountManager.get(context);
        String baseurl = ama.getUserData(account, "oc_base_url");
        String name = account.name.substring(0, account.name.indexOf('@'));
        String url = baseurl + "/index.php/avatar/" + name + "/" + size;
        ThumbnailsCacheManager.LoadMenuImage(url, accountMenuItem, account.name, true, getResources(), fromCache);
    }

    private void setAvatar(Account account, Context context, int size, Resources resources, ImageView accountMenuItem, boolean fromCache) {
        AccountManager ama = AccountManager.get(context);
        String baseurl = ama.getUserData(account, "oc_base_url");
        String name = account.name.substring(0, account.name.indexOf('@'));
        String url = baseurl + "/index.php/avatar/" + name + "/" + size;
        ThumbnailsCacheManager.LoadImage(url, accountMenuItem, account.name, true, fromCache);
    }
    /**
     * sets the given account name in the drawer in case the drawer is available. The account name is shortened
     * beginning from the @-sign in the username.
     *
     * @param account the account to be set in the drawer
     */
    protected void setAccountInDrawer(Account account) {
        if (mDrawerLayout != null && account != null) {
            TextView username = (TextView) findNavigationViewChildById(R.id.drawer_username);
            TextView usernameFull = (TextView) findNavigationViewChildById(R.id.drawer_username_full);
            usernameFull.setText(account.name);
            username.setText(account.name);

            ImageView im = (ImageView) findNavigationViewChildById(R.id.drawer_current_account);

            AccountManager accountMgr = AccountManager.get(getApplicationContext());
            String serverUrl = accountMgr.getUserData(account, "oc_base_url");
            String displayName = accountMgr.getUserData(account, "oc_display_name");

            String name = account.name.substring(0, account.name.indexOf('@'));
            int size = (int) mMenuAccountAvatarRadiusDimension * 2;
            String url = serverUrl + "/index.php/avatar/" + name + "/" + size;
            ThumbnailsCacheManager.LoadImage(url, im, displayName, true, true);

        }
    }

    /**
     * Toggle between standard menu and account list including saving the state.
     */
    private void toggleAccountList() {
        mIsAccountChooserActive = !mIsAccountChooserActive;
        showMenu();
    }

    /**
     * depending on the #mIsAccountChooserActive flag shows the account chooser or the standard menu.
     */
    private void showMenu() {
        if (mNavigationView != null) {
            if (mIsAccountChooserActive) {
                mAccountChooserToggle.setImageResource(R.drawable.ic_up);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_accounts, true);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_standard, false);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_navigation, false);
            } else {
                mAccountChooserToggle.setImageResource(R.drawable.ic_down);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_accounts, false);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_standard, true);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_navigation, true);
            }
        }
    }

    /**
     * shows or hides the quota UI elements.
     *
     * @param showQuota show/hide quota information
     */
    private void showQuota(boolean showQuota) {
        if (showQuota) {
            mQuotaView.setVisibility(View.VISIBLE);
        } else {
            mQuotaView.setVisibility(View.GONE);
        }
    }

    /**
     * checks/highlights the provided menu item if the drawer has been initialized and the menu item exists.
     *
     * @param menuItemId the menu item to be highlighted
     */
    protected void setDrawerMenuItemChecked(int menuItemId) {
        if (mNavigationView != null && mNavigationView.getMenu() != null && mNavigationView.getMenu().findItem
                (menuItemId) != null) {
            mNavigationView.getMenu().findItem(menuItemId).setChecked(true);
            mCheckedMenuItem = menuItemId;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThumbnailsCacheManager.ThumbnailsCacheManagerInit(getApplicationContext());

        if (savedInstanceState != null) {
            mIsAccountChooserActive = savedInstanceState.getBoolean(KEY_IS_ACCOUNT_CHOOSER_ACTIVE, false);
            mCheckedMenuItem = savedInstanceState.getInt(KEY_CHECKED_MENU_ITEM, Menu.NONE);
        }
        mCurrentAccountAvatarRadiusDimension = getResources()
                .getDimension(R.dimen.nav_drawer_header_avatar_radius);
        mOtherAccountAvatarRadiusDimension = getResources()
                .getDimension(R.dimen.nav_drawer_header_avatar_other_accounts_radius);
        mMenuAccountAvatarRadiusDimension = getResources()
                .getDimension(R.dimen.nav_drawer_menu_avatar_radius);

    }

    protected void onStatusTextChanged() {

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(KEY_IS_ACCOUNT_CHOOSER_ACTIVE, mIsAccountChooserActive);
        outState.putInt(KEY_CHECKED_MENU_ITEM, mCheckedMenuItem);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mIsAccountChooserActive = savedInstanceState.getBoolean(KEY_IS_ACCOUNT_CHOOSER_ACTIVE, false);
        mCheckedMenuItem = savedInstanceState.getInt(KEY_CHECKED_MENU_ITEM, Menu.NONE);

        // (re-)setup drawer state
        showMenu();

        // check/highlight the menu item if present
        if (mCheckedMenuItem > Menu.NONE || mCheckedMenuItem < Menu.NONE) {
            setDrawerMenuItemChecked(mCheckedMenuItem);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();
            if (isDrawerOpen()) {
                mDrawerToggle.setDrawerIndicatorEnabled(true);
            }
        }
        updateAccountList();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onBackPressed() {
        if (isDrawerOpen()) {
            closeDrawer();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onResume() {
        super.onResume();
        setDrawerMenuItemChecked(mCheckedMenuItem);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // update Account list and active account if Manage Account activity replies with
        // - ACCOUNT_LIST_CHANGED = true
        // - RESULT_OK

    }

    /**
     * Finds a view that was identified by the id attribute from the drawer header.
     *
     * @param id the view's id
     * @return The view if found or <code>null</code> otherwise.
     */
    private View findNavigationViewChildById(int id) {
        return ((NavigationView) findViewById(R.id.nav_view)).getHeaderView(0).findViewById(id);
    }


    public boolean setCurrentOwnCloudAccount(Context context, String accountName) {
        boolean result = false;
        if (accountName != null) {
            boolean found;
            for (Account account : getAccounts(context)) {
                found = (account.name.equals(accountName));
                if (found) {
                    SharedPreferences.Editor appPrefs = PreferenceManager
                            .getDefaultSharedPreferences(context).edit();
                    appPrefs.putString("select_oc_account", accountName);

                    appPrefs.commit();
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Can be used to get the currently selected ownCloud {@link Account} in the
     * application preferences.
     *
     * @param   context     The current application {@link Context}
     * @return              The ownCloud {@link Account} currently saved in preferences, or the first
     *                      {@link Account} available, if valid (still registered in the system as ownCloud
     *                      account). If none is available and valid, returns null.
     */
    public Account getCurrentOwnCloudAccount(Context context) {
        Account[] ocAccounts = getAccounts(context);
        Account defaultAccount = null;

        SharedPreferences appPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        String accountName = appPreferences
                .getString("select_oc_account", null);

        // account validation: the saved account MUST be in the list of ownCloud Accounts known by the AccountManager
        if (accountName != null) {
            for (Account account : ocAccounts) {
                if (account.name.equals(accountName)) {
                    defaultAccount = account;
                    break;
                }
            }
        }

        if (defaultAccount == null && ocAccounts.length != 0) {
            // take first account as fallback
            defaultAccount = ocAccounts[0];
        }

        return defaultAccount;
    }

    public Account[] getAccounts(Context context) {
        AccountManager accountManager = AccountManager.get(context);
        return accountManager.getAccountsByType(getResources().getString(R.string.account_type));
    }


    /**
     * populates the avatar drawer array with the first three ownCloud {@link Account}s while the first element is
     * always the current account.
     */
    private void populateDrawerOwnCloudAccounts() {
        mAvatars = new Account[3];
        Account[] accountsAll = AccountManager.get(this).getAccountsByType(getResources().getString(R.string.account_type));
        Account currentAccount = getCurrentOwnCloudAccount(this);

        mAvatars[0] = currentAccount;
        int j = 0;
        for (int i = 1; i <= 2 && i < accountsAll.length && j < accountsAll.length; j++) {
            if (!currentAccount.equals(accountsAll[j])) {
                mAvatars[i] = accountsAll[j];
                i++;
            }
        }
    }



    /**
     * Adds other listeners to react on changes of the drawer layout.
     *
     * @param listener      Object interested in changes of the drawer layout.
     */
    public void addDrawerListener(DrawerLayout.DrawerListener listener) {
        if (mDrawerLayout != null) {
            mDrawerLayout.addDrawerListener(listener);
        }
    }
}
