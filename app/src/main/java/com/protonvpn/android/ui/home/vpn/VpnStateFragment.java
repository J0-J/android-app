/*
 * Copyright (c) 2021 Proton Technologies AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.protonvpn.android.ui.home.vpn;

import android.animation.LayoutTransition;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.github.clans.fab.FloatingActionMenu;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.jobs.MoveViewJob;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.color.MaterialColors;
import com.protonvpn.android.R;
import com.protonvpn.android.api.ProtonApiRetroFit;
import com.protonvpn.android.appconfig.AppConfig;
import com.protonvpn.android.bus.ConnectToProfile;
import com.protonvpn.android.bus.ConnectedToServer;
import com.protonvpn.android.bus.EventBus;
import com.protonvpn.android.bus.TrafficUpdate;
import com.protonvpn.android.components.BaseFragment;
import com.protonvpn.android.components.ContentLayout;
import com.protonvpn.android.components.NetShieldSwitch;
import com.protonvpn.android.components.VPNException;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.ui.home.ServerListUpdater;
import com.protonvpn.android.ui.ServerLoadColor;
import com.protonvpn.android.ui.onboarding.OnboardingDialogs;
import com.protonvpn.android.ui.onboarding.OnboardingPreferences;
import com.protonvpn.android.utils.AnimationTools;
import com.protonvpn.android.utils.ConnectionTools;
import com.protonvpn.android.utils.DebugUtils;
import com.protonvpn.android.utils.HtmlTools;
import com.protonvpn.android.utils.Log;
import com.protonvpn.android.utils.ProtonLogger;
import com.protonvpn.android.utils.ServerManager;
import com.protonvpn.android.utils.TimeUtils;
import com.protonvpn.android.utils.TrafficMonitor;
import com.protonvpn.android.utils.ViewUtils;
import com.protonvpn.android.vpn.RetryInfo;
import com.protonvpn.android.vpn.VpnConnectionManager;
import com.protonvpn.android.vpn.VpnState;
import com.protonvpn.android.vpn.VpnStateMonitor;

import java.util.List;
import java.util.Timer;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import butterknife.BindView;
import butterknife.OnClick;
import de.hdodenhof.circleimageview.CircleImageView;

import static com.protonvpn.android.utils.AndroidUtilsKt.openProtonUrl;

@ContentLayout(R.layout.vpn_state_fragment)
public class VpnStateFragment extends BaseFragment {

    private static final String KEY_ERROR_CONNECTION_ID = "error_connection_id";
    private static final String KEY_DISMISSED_CONNECTION_ID = "dismissed_connection_id";

    @BindView(R.id.connectingView) View connectingView;
    @BindView(R.id.textConnectingTo) TextView textConnectingTo;
    @BindView(R.id.imageExpand) ImageView imageExpand;
    @BindView(R.id.layoutBottomSheet) View layoutBottomSheet;
    @BindView(R.id.progressBar) ProgressBar progressBar;

    @BindView(R.id.layoutError) View layoutError;
    @BindView(R.id.layoutConnecting) View layoutConnecting;
    @BindView(R.id.layoutNotConnected) View layoutNotConnected;
    @BindView(R.id.layoutConnected) View layoutConnected;
    @BindView(R.id.statusDivider) View statusDivider;

    @BindView(R.id.chart) LineChart chart;

    @BindView(R.id.textCurrentIp) TextView textCurrentIp;
    @BindView(R.id.textServerName) TextView textServerName;
    @BindView(R.id.textServerIp) TextView textServerIp;
    @BindView(R.id.textDownloadSpeed) TextView textDownloadSpeed;
    @BindView(R.id.textUploadSpeed) TextView textUploadSpeed;
    @BindView(R.id.textDownloadVolume) TextView textDownloadVolume;
    @BindView(R.id.textUploadVolume) TextView textUploadVolume;
    @BindView(R.id.textProtocol) TextView textProtocol;
    @BindView(R.id.textSessionTime) TextView textSessionTime;
    @BindView(R.id.textError) TextView textError;
    @BindView(R.id.textSupport) TextView textSupport;
    @BindView(R.id.progressBarError) ProgressBar progressBarError;
    @BindView(R.id.textLoad) TextView textLoad;
    @BindView(R.id.imageLoad) CircleImageView imageLoad;
    @BindView(R.id.buttonCancel) Button buttonCancel;
    @BindView(R.id.netShieldSwitch) NetShieldSwitch switchNetShield;

    @Inject ProtonApiRetroFit api;
    @Inject ServerManager manager;
    @Inject UserData userData;
    @Inject AppConfig appConfig;
    @Inject VpnStateMonitor stateMonitor;
    @Inject VpnConnectionManager vpnConnectionManager;
    @Inject ServerListUpdater serverListUpdater;
    @Inject TrafficMonitor trafficMonitor;
    private BottomSheetBehavior bottomSheetBehavior;
    private long errorConnectionID;
    private long dismissedConnectionID;
    private Timer graphUpdateTimer;

    @OnClick(R.id.buttonQuickConnect)
    public void buttonQuickConnect() {
        Profile defaultProfile = manager.getDefaultConnection();
        EventBus.post(new ConnectToProfile(defaultProfile));
    }

    @OnClick(R.id.buttonCancel)
    public void buttonCancel() {
        ProtonLogger.INSTANCE.log("Canceling connection");
        vpnConnectionManager.disconnect();
        changeBottomSheetState(false);
    }

    @OnClick(R.id.buttonCancelRetry)
    public void buttonCancelRetry() {
        vpnConnectionManager.disconnect();
    }

    @OnClick(R.id.buttonDisconnect)
    public void buttonDisconnect() {
        buttonCancel();
    }

    @OnClick(R.id.textSupport)
    public void textSupport() {
        openProtonUrl(getActivity(), "https://protonvpn.com/support/solutions-android-vpn-app-issues/");
    }

    @OnClick(R.id.buttonSaveToProfile)
    public void buttonSaveToProfile() {
        Profile currentProfile = stateMonitor.getConnectionProfile();
        for (Profile profile : manager.getSavedProfiles()) {
            if (profile.getServer().getDomain().equals(currentProfile.getServer().getDomain())) {
                Toast.makeText(getActivity(), R.string.saveProfileAlreadySaved, Toast.LENGTH_LONG).show();
                return;
            }
        }
        manager.addToProfileList(currentProfile.getServer().getServerName(),
            Profile.Companion.getRandomProfileColor(getContext()), currentProfile.getServer());
        Toast.makeText(getActivity(), R.string.toastProfileSaved, Toast.LENGTH_LONG).show();
    }

    @OnClick(R.id.layoutCollapsedStatus)
    public void layoutCollapsedStatus() {
        if (bottomSheetBehavior != null) {
            changeBottomSheetState(bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    @OnClick(R.id.buttonRetry)
    public void buttonRetry() {
        vpnConnectionManager.reconnect(getContext());
    }

    @Override
    public void onViewCreated() {
        registerForEvents();
        updateNotConnectedView();
        forceAnimeNestedLayouts();
        serverListUpdater.getIpAddress()
            .observe(getViewLifecycleOwner(), (ip) -> textCurrentIp.setText(textCurrentIp.getContext()
                .getString(R.string.notConnectedCurrentIp,
                    ip.isEmpty() ? getString(R.string.stateFragmentUnknownIp) : ip)));
        switchNetShield.init(userData.getNetShieldProtocol(), appConfig, getViewLifecycleOwner(), userData,
            stateMonitor, vpnConnectionManager, s -> {
                userData.setNetShieldProtocol(s);
                return null;
            });
        userData.getNetShieldLiveData().observe(getViewLifecycleOwner(), state -> {
            if (state != null) {
                switchNetShield.setNetShieldValue(state);
            }
        });
        initChart();

        stateMonitor.getStatusLiveData().observe(getViewLifecycleOwner(), state -> updateView(false, state));
        trafficMonitor
            .getTrafficStatus()
            .observe(getViewLifecycleOwner(), this::onTrafficUpdate);
    }

    private void forceAnimeNestedLayouts() {
        LayoutTransition layoutTransition = ((ViewGroup) layoutConnected).getLayoutTransition();
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
    }

    @Override
    public void onDestroyView() {
        // Workaround for charting library memory leak
        // https://github.com/PhilJay/MPAndroidChart/issues/2238
        MoveViewJob.getInstance(null, 0, 0, null, null);

        super.onDestroyView();
    }

    public boolean isBottomSheetExpanded() {
        return bottomSheetBehavior != null
            && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED;
    }

    private void initChart() {
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setViewPortOffsets(0, 0, 0, 0);
        chart.getLegend().setEnabled(false);

        chart.setDrawGridBackground(false);
        chart.setMaxHighlightDistance(300);

        YAxis y = chart.getAxisLeft();
        y.setDrawLabels(true);
        y.setDrawGridLines(true);
        y.setDrawLimitLinesBehindData(true);
        y.setAxisLineColor(ContextCompat.getColor(chart.getContext(), R.color.transparentWhite));

        XAxis x = chart.getXAxis();
        x.setDrawLabels(true);
        x.setDrawAxisLine(true);
        x.setDrawLimitLinesBehindData(true);
        x.setDrawGridLines(true);
        x.setAxisLineColor(ContextCompat.getColor(chart.getContext(), R.color.transparentWhite));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        errorConnectionID = 0;
        dismissedConnectionID = 0;
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_ERROR_CONNECTION_ID)) {
            errorConnectionID = (Long) savedInstanceState.getSerializable(KEY_ERROR_CONNECTION_ID);
            dismissedConnectionID = (Long) savedInstanceState.getSerializable(KEY_DISMISSED_CONNECTION_ID);
        }
    }

    public void initStatusLayout(final FloatingActionMenu attachedButton) {
        bottomSheetBehavior = BottomSheetBehavior.from(layoutBottomSheet);
        bottomSheetBehavior.setPeekHeight(AnimationTools.convertDpToPixel(56));
        bottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED && appConfig.getFeatureFlags().getNetShieldEnabled()
                    && switchNetShield.isSwitchVisible()) {
                    OnboardingDialogs.showDialogOnView(getContext(), switchNetShield,
                        getString(R.string.netshield), getString(R.string.onboardingNetshield),
                        OnboardingPreferences.NETSHIELD_DIALOG);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                attachedButton.animate().alpha(1 - slideOffset).setDuration(0).start();
                attachedButton.setVisibility(slideOffset == 1 ? View.GONE : View.VISIBLE);
                if (imageExpand != null) {
                    imageExpand.animate().rotation(180 * slideOffset).setDuration(0).start();
                }
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable(KEY_ERROR_CONNECTION_ID, errorConnectionID);
        outState.putSerializable(KEY_DISMISSED_CONNECTION_ID, dismissedConnectionID);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (graphUpdateTimer != null) {
            graphUpdateTimer.cancel();
            graphUpdateTimer = null;
        }
    }

    public boolean collapseBottomSheet() {
        if (bottomSheetBehavior != null
            && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            changeBottomSheetState(false);
            return true;
        }
        return false;
    }

    public boolean openBottomSheet() {
        if (bottomSheetBehavior != null
            && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            changeBottomSheetState(true);
            return true;
        }
        return false;
    }

    private void initConnectingStateView(@Nullable Server profile, boolean fromSavedState) {
        switchNetShield.setVisibility(View.GONE);
        layoutConnected.setVisibility(View.GONE);
        layoutNotConnected.setVisibility(View.GONE);
        layoutConnecting.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);

        // isTest(): ugly but enables running UI tests on android 5/6 (which have a problem with this view)
        progressBar.setVisibility(DebugUtils.INSTANCE.isTest(getActivity()) ? View.INVISIBLE : View.VISIBLE);

        connectingView.setBackgroundColor(MaterialColors.getColor(connectingView,
            profile != null ? (userData.isSecureCoreEnabled() ? R.attr.colorAccent : R.attr.colorPrimary) :
                R.attr.colorPrimary));
        textConnectingTo.setTextColor(ContextCompat.getColor(textConnectingTo.getContext(), R.color.white));

        if (!fromSavedState) {
            changeBottomSheetState(true);
        }
    }

    private void onTrafficUpdate(final @Nullable TrafficUpdate update) {
        if (getActivity() != null && update != null) {
            addEntry(update.getDownloadSpeed(), update.getUploadSpeed());
            textSessionTime.setText(TimeUtils.getFormattedTimeFromSeconds(update.getSessionTimeSeconds()));
            textUploadSpeed.setText(update.getUploadSpeedString());
            textDownloadSpeed.setText(update.getDownloadSpeedString());
            textUploadVolume.setText(ConnectionTools.bytesToSize(update.getSessionUpload()));
            textDownloadVolume.setText(ConnectionTools.bytesToSize(update.getSessionDownload()));
        }
    }

    private void clearConnectedStatus() {
        if (graphUpdateTimer != null) {
            graphUpdateTimer.cancel();
            graphUpdateTimer = null;
        }
        onTrafficUpdate(new TrafficUpdate(0, 0, 0, 0, 0));
        chart.clear();
    }

    private void updateNotConnectedView() {
        layoutConnected.setVisibility(View.GONE);
        layoutNotConnected.setVisibility(View.VISIBLE);
        layoutConnecting.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        switchNetShield.setVisibility(View.VISIBLE);

        connectingView.setBackgroundColor(MaterialColors.getColor(connectingView, R.attr.colorPrimary));
        imageExpand.setImageResource(R.drawable.ic_up_white);
        textConnectingTo.setTextColor(ContextCompat.getColor(textConnectingTo.getContext(), R.color.white));
    }

    private void initConnectedStateView(Server server) {
        chart.getViewTreeObserver().addOnGlobalLayoutListener(
            new ViewTreeObserver.OnGlobalLayoutListener(){
                @Override
                public void onGlobalLayout() {
                    float chartHeightInDp = ViewUtils.INSTANCE.convertPixelsToDp(chart.getHeight());
                    if (chartHeightInDp < 100f) {
                        chart.setVisibility(View.GONE);
                    }
                    chart.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });
        switchNetShield.setVisibility(View.VISIBLE);
        layoutConnected.setVisibility(View.VISIBLE);
        layoutNotConnected.setVisibility(View.GONE);
        layoutConnecting.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        textConnectingTo.setTextColor(ContextCompat.getColor(textConnectingTo.getContext(),
            server.isSecureCoreServer() ? R.color.white : R.color.black));

        imageExpand.setImageResource(
            server.isSecureCoreServer() ? R.drawable.ic_up_white : R.drawable.ic_up_black);
        int bgColor = server.isSecureCoreServer()
            ? MaterialColors.getColor(connectingView, R.attr.colorAccent)
            : ContextCompat.getColor(connectingView.getContext(), R.color.white);
        connectingView.setBackgroundColor(bgColor);

        textServerName.setText(server.getServerName());
        textServerIp.setText(stateMonitor.getExitIP());
        textProtocol.setText(stateMonitor.getConnectionProtocol().displayName(false));
        int load = (int) server.getLoad();
        textLoad.setText(textLoad.getContext().getString(R.string.serverLoad, String.valueOf(load)));
        imageLoad.setImageDrawable(new ColorDrawable(
            ServerLoadColor.getColor(imageLoad, server.getLoadState())));
    }

    private void changeBottomSheetState(boolean expand) {
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(
                expand ? BottomSheetBehavior.STATE_EXPANDED : BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    private void addEntry(float downloadSpeed, float uploadSpeed) {
        if (chart.getData() == null) {
            chart.setData(new LineData());
        }
        LineData data = chart.getData();
        ILineDataSet set = data.getDataSetByIndex(0);
        ILineDataSet set2 = data.getDataSetByIndex(1);

        if (set == null && set2 == null) {
            set = initDataSet(null, "ABE560");
            set2 = initDataSet(null, "66D2D8");
            data.addDataSet(set2);
            data.addDataSet(set);
        }

        data.addEntry(new Entry(data.getDataSetByIndex(0).getEntryCount(), downloadSpeed), 0);
        data.addEntry(new Entry(data.getDataSetByIndex(1).getEntryCount(), uploadSpeed), 1);
        data.notifyDataChanged();
        chart.notifyDataSetChanged();

        chart.setVisibleXRangeMaximum(20);
        chart.moveViewTo(data.getEntryCount(), 10f, YAxis.AxisDependency.LEFT);
    }

    private LineDataSet initDataSet(List<Entry> values, String color) {
        LineDataSet set1;
        set1 = new LineDataSet(values, "DataSet");
        set1.setDrawIcons(false);
        set1.setColor(Color.parseColor("#" + color));
        set1.setLineWidth(2f);
        set1.setDrawValues(false);
        set1.setDrawCircleHole(false);
        set1.setDrawCircles(false);
        set1.setFillColor(Color.parseColor("#66" + color));
        set1.setDrawFilled(true);
        set1.setFormLineWidth(1f);
        set1.setFormSize(15.f);
        set1.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
        return set1;
    }

    public void updateView(boolean fromSavedState, @NonNull VpnStateMonitor.Status vpnState) {
        Profile profile = vpnState.getProfile();

        String serverName = "";
        Server connectedServer = null;
        if (profile != null) {
            serverName = (profile.isPreBakedProfile() || profile.getDisplayName(getContext()).isEmpty())
                && stateMonitor.getConnectingToServer() != null ?
                stateMonitor.getConnectingToServer().getDisplayName() : profile.getDisplayName(getContext());
            connectedServer = vpnState.getServer();
        }
        if (isAdded()) {
            statusDivider.setVisibility(View.VISIBLE);
            VpnState state = vpnState.getState();
            //TODO: migrate to kotlin to use "when" here
            if (state instanceof VpnState.Error) {
                reportError(((VpnState.Error) vpnState.getState()));
            }
            else if (VpnState.Disabled.INSTANCE.equals(state)) {
                checkDisconnectFromOutside();
                textConnectingTo.setText(R.string.loaderNotConnected);
                updateNotConnectedView();
            }
            else if (VpnState.CheckingAvailability.INSTANCE.equals(state)
                || VpnState.ScanningPorts.INSTANCE.equals(state)) {
                statusDivider.setVisibility(View.GONE);
                textConnectingTo.setText(R.string.loaderCheckingAvailability);
                initConnectingStateView(connectedServer, fromSavedState);
            }
            else if (VpnState.Connecting.INSTANCE.equals(state)) {
                statusDivider.setVisibility(View.GONE);
                textConnectingTo.setText(getString(R.string.loaderConnectingTo, serverName));
                initConnectingStateView(connectedServer, fromSavedState);
            }
            else if (VpnState.WaitingForNetwork.INSTANCE.equals(state)) {
                statusDivider.setVisibility(View.GONE);
                textConnectingTo.setText(R.string.loaderReconnectNoNetwork);
                initConnectingStateView(connectedServer, fromSavedState);
            }
            else if (VpnState.Connected.INSTANCE.equals(state)) {
                textConnectingTo.setText(getString(R.string.loaderConnectedTo, serverName));
                initConnectedStateView(connectedServer);
            }
            else if (VpnState.Disconnecting.INSTANCE.equals(state)) {
                textConnectingTo.setText(R.string.loaderDisconnecting);
                connectingView.setBackgroundColor(
                    MaterialColors.getColor(requireView(), R.attr.colorPrimary));
                textConnectingTo.setTextColor(ContextCompat.getColor(getContext(), R.color.white));
                imageExpand.setImageResource(R.drawable.ic_up_white);
                clearConnectedStatus();
            }
            else {
                updateNotConnectedView();
            }
        }
    }

    private void checkDisconnectFromOutside() {
        if (stateMonitor.isConnected()) {
            EventBus.getInstance().post(new ConnectedToServer(null));
            imageExpand.setImageResource(R.drawable.ic_up_white);
        }
    }

    private boolean reportError(VpnState.Error error) {
        Log.e("report error: " + error.toString());
        switch (error.getType()) {
            case AUTH_FAILED:
                showAuthError(R.string.error_auth_failed);
                break;
            case PEER_AUTH_FAILED:
                showErrorDialog(R.string.error_peer_auth_failed);
                Log.exception(new VPNException("Peer Auth: Verifying gateway authentication failed"));
                break;
            case LOOKUP_FAILED:
                showErrorDialog(R.string.error_lookup_failed);
                Log.exception(new VPNException("Gateway address lookup failed"));
                break;
            case UNREACHABLE:
                showErrorDialog(R.string.error_unreachable);
                Log.exception(new VPNException("Gateway is unreachable"));
                break;
            case MAX_SESSIONS:
                Log.exception(new VPNException("Maximum number of sessions used"));
                break;
            case UNPAID:
                showAuthError(HtmlTools.fromHtml(getString(R.string.errorUserDelinquent)));
                Log.exception(new VPNException("Overdue payment"));
                break;
            case MULTI_USER_PERMISSION:
                vpnConnectionManager.disconnect();
                showAuthError(R.string.errorTunMultiUserPermission);
                Log.exception(new VPNException("Dual-apps permission error"));
                break;
            case LOCAL_AGENT_ERROR:
                vpnConnectionManager.disconnect();
                showAuthError(getString(R.string.errorWireguardWithPlaceholder, error.getDescription()));
                Log.exception(new VPNException("Wireguard error: " + error.getDescription()));
                break;
            default:
                showErrorDialog(R.string.error_generic);
                Log.exception(new VPNException("Unspecified failure while connecting"));
                break;
        }

        return true;
    }

    private void showAuthError(@StringRes int stringRes) {
        showAuthError(getString(stringRes));
    }

    private void showAuthError(CharSequence content) {
        new MaterialDialog.Builder(getActivity()).theme(Theme.DARK)
            .title(R.string.dialogTitleAttention)
            .content(content)
            .cancelable(false)
            .negativeText(R.string.close)
            .onNegative((dialog, which) -> vpnConnectionManager.disconnect())
            .show();
    }

    private void showErrorDialog(@StringRes int textId) {
        layoutConnected.setVisibility(View.GONE);
        layoutNotConnected.setVisibility(View.GONE);
        layoutConnecting.setVisibility(View.GONE);
        layoutError.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
        textError.setText(textId);

        RetryInfo retryInfo = vpnConnectionManager.getRetryInfo();
        progressBarError.setVisibility(retryInfo != null ? View.VISIBLE : View.INVISIBLE);
        if (retryInfo != null) {
            progressBarError.setMax(retryInfo.getTimeoutSeconds());
            progressBarError.setProgress(retryInfo.getRetryInSeconds());
            int retryIn = retryInfo.getRetryInSeconds();
            textConnectingTo.setText(getResources().getQuantityString(R.plurals.retry_in, retryIn, retryIn));
        }
        else {
            textConnectingTo.setText(R.string.loaderReconnecting);
        }
        boolean showSupportLink = textId == R.string.error_lookup_failed;

        textSupport.setPaintFlags(textSupport.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        textSupport.setVisibility(showSupportLink ? View.VISIBLE : View.GONE);
    }
}
