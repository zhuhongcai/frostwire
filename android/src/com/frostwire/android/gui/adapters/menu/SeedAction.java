/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.adapters.menu;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.dialogs.YesNoDialog;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.*;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.alerts.DhtBootstrapAlert;
import com.frostwire.jlibtorrent.swig.*;
import com.frostwire.logging.Logger;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.Transfer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created on 4/22/16.
 *
 * @author gubatron
 * @author aldenml
 */
public class SeedAction extends MenuAction implements AbstractDialog.OnDialogClickListener {
    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(SeedAction.class);
    private final FileDescriptor fd;
    private final List<FileDescriptor> fds;
    private final BittorrentDownload btDownload;
    private final Transfer transferToClear;
    private static String DLG_SEEDING_OFF_TAG = "DLG_SEEDING_OFF_TAG";
    private static String DLG_TURN_BITTORRENT_BACK_ON = "DLG_TURN_BITTORRENT_BACK_ON";

    // TODO: Receive extra metadata that could be put/used in the torrent for
    // enriched announcement.

    private SeedAction(Context context,
                       FileDescriptor fd,
                       List<FileDescriptor> fds,
                       BittorrentDownload existingBittorrentDownload,
                       Transfer transferToClear) {
        super(context, R.drawable.contextmenu_icon_play_transfer, R.string.seed);
        this.fd = fd;
        this.fds = fds;
        this.btDownload = existingBittorrentDownload;
        this.transferToClear = transferToClear;
    }

    // Reminder: Currently disabled when using SD Card.
    public SeedAction(Context context, FileDescriptor fd) {
        this(context, fd, null, null, null);
    }

    // Reminder: Currently disabled when using SD Card.
    public SeedAction(Context context, FileDescriptor fd, Transfer transferToClear) {
        this(context, fd, null, null, transferToClear);
    }

    // Reminder: Currently disabled when using SD Card.
    public SeedAction(Context context, List<FileDescriptor> checked) {
        this(context, null, checked, null, null);
    }

    // This one is not disabled as it's meant for existing torrent transfers.
    public SeedAction(Context context, BittorrentDownload download) {
        this(context, null, null, download,null);
    }

    @Override
    protected void onClick(Context context) {
        // NOTES.
        // Performance note: (specially when creating a .torrent of a big video)
        // wish we could know in advance if we've already created it
        // and have a .torrent already for this file on disk.
        // I think we could keep them in a db table(filepath -> sha1_hash)
        // and then we could just look it up on the session.
        // For now we just create the <file-name>-<infohash>.torrent after
        // we've added the TorrentInfo to the session.
        // Note: Let's try Merkle torrents to keep them small and use less
        // storage on the android device.

        // if BitTorrent is turned off
        if (TransferManager.instance().isBittorrentDisconnected()) {
            showBittorrentDisconnectedDialog();
            return;
        }

        // in case user seeds only on wifi and there's no wifi, we let them know what will occur.
        if (seedingOnlyOnWifiButNoWifi()) {
            showNoWifiInformationDialog();
        }

        // 1. If Seeding is turned off let's ask the user if they want to
        //    turn seeding on, or else cancel this.
        if (!TransferManager.instance().isSeedingEnabled()) {
            showSeedingDialog();
        } else {
            seedEm();
            UIUtils.showTransfersOnDownloadStart(getContext());
        }
    }

    private void showNoWifiInformationDialog() {
        ShowNoWifiInformationDialog.newInstance().show(((Activity) getContext()).getFragmentManager());
    }

    private void showBittorrentDisconnectedDialog() {
        YesNoDialog dlg = YesNoDialog.newInstance(
            DLG_TURN_BITTORRENT_BACK_ON,
             R.string.bittorrent_off,
             R.string.bittorrent_is_currently_disconnected_would_you_like_me_to_start_it_for_you);
        dlg.setOnDialogClickListener(this);
        dlg.show(((Activity) getContext()).getFragmentManager());
    }

    private void showSeedingDialog() {
        YesNoDialog dlg = YesNoDialog.newInstance(
                DLG_SEEDING_OFF_TAG,
                R.string.enable_seeding,
                R.string.seeding_is_currently_disabled);
        dlg.setOnDialogClickListener(this);
        dlg.show(((Activity) getContext()).getFragmentManager());
    }

    @Override
    public void onDialogClick(String tag, int which) {
        if (tag.equals(DLG_SEEDING_OFF_TAG)) {
            if (which == Dialog.BUTTON_NEGATIVE) {
                UIUtils.showLongMessage(getContext(),
                        R.string.the_file_could_not_be_seeded_enable_seeding);
            } else if (which == Dialog.BUTTON_POSITIVE) {
                onSeedingEnabled();
            }
        }

        if (tag.equals(DLG_TURN_BITTORRENT_BACK_ON)) {
            if (which == Dialog.BUTTON_NEGATIVE) {
                UIUtils.showLongMessage(getContext(),
                        R.string.the_file_could_not_be_seeded_bittorrent_will_remain_disconnected);
            } else if (which == Dialog.BUTTON_POSITIVE) {
                onBittorrentConnect();
            }
        }
    }

    private boolean seedingOnlyOnWifiButNoWifi() {
        return TransferManager.instance().isSeedingEnabled() && TransferManager.instance().isSeedingEnabledOnlyForWifi() && !NetworkManager.instance().isDataWIFIUp();
    }

    private void seedEm() {
        if (fd != null) {
            seedFileDescriptor(fd);
        } else if (fds != null) {
            seedFileDescriptors();
        } else if (btDownload != null) {
            seedBTDownload();
        }

        if (transferToClear != null) {
            TransferManager.instance().remove(transferToClear);
        }
    }

    private void seedFileDescriptor(FileDescriptor fd) {
        if (fd.filePath.endsWith(".torrent")) {
            BTEngine.getInstance().download(new File(fd.filePath), null, new boolean[]{true});
        } else {
            buildTorrentAndSeedIt(fd);
        }
    }

    private void seedFileDescriptors() {
        for (FileDescriptor f : fds) {
            seedFileDescriptor(f);
        }
    }

    private void seedBTDownload() {
        btDownload.resume();
        final TorrentHandle torrentHandle = BTEngine.getInstance().getSession().findTorrent(new Sha1Hash(btDownload.getInfoHash()));
        if (torrentHandle != null) {
            forceDHTAnnounceIfNoPeers(torrentHandle, null);
        } else {
            LOG.warn("seedBTDownload() could not find torrentHandle for existing torrent.");
        }
    }

    private void buildTorrentAndSeedIt(final FileDescriptor fd) {
        // TODO: Do this so it works with SD Card support / New BS File storage api from Android.
        Engine.instance().getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                File file = new File(fd.filePath);
                File saveDir = file.getParentFile();
                file_storage fs = new file_storage();
                fs.add_file(file.getName(), file.length());
                fs.set_name(file.getName());
                create_torrent ct = new create_torrent(fs); //, 0, -1, create_torrent.flags_t.merkle.swigValue());
                // commented out the merkle flag above because torrent doesn't appear as "Seeding", piece count doesn't work
                // as the algorithm in BTDownload.getProgress() doesn't make sense at the moment for merkle torrents.
                ct.set_creator("FrostWire " + Constants.FROSTWIRE_VERSION_STRING + " build " + Constants.FROSTWIRE_BUILD);
                ct.set_priv(false);

                final error_code ec = new error_code();
                libtorrent.set_piece_hashes_ex(ct, saveDir.getAbsolutePath(), new set_piece_hashes_listener(), ec);

                final byte[] torrent_bytes = new Entry(ct.generate()).bencode();
                final TorrentInfo tinfo = TorrentInfo.bdecode(torrent_bytes);

                // IDEAS:
                // 1. Add DHT router
                //see http://github.com/bittorrent/bootstrap-dht
                //tinfo.addNode("dht.frostwire.com",1234);

                // 2. Create a CanonicalDHTAnnouncer which keeps track
                // of nodes we could use for DHT-announcing-bootstrapping.
                // Issue a GET_PEERS request on startup, and out of those
                // peers do a tinfo.tinfo.addNode() of at least 5 DHT
                // bootstraping nodes for this torrent. This way we don't
                // have to way for a GET_PEERS_REPLY message to create the
                // .torrent.

                final Session session = BTEngine.getInstance().getSession();

                // so the TorrentHandle object is created and added to the libtorrent session.
                BTEngine.getInstance().download(tinfo, saveDir, new boolean[]{true});

                final TorrentHandle torrentHandle =
                        session.findTorrent(tinfo.infoHash());

                final DHTBootstrapListener dhtBootstrapListener = new DHTBootstrapListener(torrentHandle);
                session.addListener(dhtBootstrapListener);

                torrentHandle.saveResumeData();
                torrentHandle.pause();
                torrentHandle.setAutoManaged(true);
                torrentHandle.scrapeTracker();

                // so it will call fireDownloadUpdate(torrentHandle) -> UIBittorrentDownload.updateUI()
                // which calculates the download items;
                BTEngine.getInstance().download(tinfo, saveDir, new boolean[]{true});
                forceDHTAnnounceIfNoPeers(torrentHandle, dhtBootstrapListener);
                torrentHandle.forceRecheck();
                torrentHandle.forceReannounce();
            }
        });
    }

    private void onSeedingEnabled() {
        TransferManager.instance().enableSeeding();
        seedEm();
    }

    private void onBittorrentConnect() {
        Engine.instance().getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                Engine.instance().startServices();
                while (!Engine.instance().isStarted()) {
                    SystemClock.sleep(1000);
                }
                final Looper mainLooper = getContext().getMainLooper();
                Handler h = new Handler(mainLooper);
                h.post(new Runnable() {
                    @Override
                    public void run() {
                         onClick(getContext());
                    }
                });
            }
        });


    }

    /** TODO: Move this method somewhere more useful if it works.
     *  It could be used for smarter re-announce logic after hearing
     *  Arvid's advice. It could also be used to re-adjust the dht_announce_interval
     *  interval to allow for more capacity (longer intervals if we already have peers) */
    private static void forceDHTAnnounceIfNoPeers(final TorrentHandle torrentHandle, final AlertListener listener) {
        final ArrayList<PeerInfo> peerInfos = torrentHandle.peerInfo();
        final TorrentStatus status = torrentHandle.getStatus();
        final ArrayList<Pair<String, Integer>> dhtNodes = torrentHandle.getTorrentInfo().nodes();
        LOG.info("================================================");
        LOG.info("list peers        : " + status.getListPeers());
        LOG.info("DHT Nodes         : " + dhtNodes.size());
        LOG.info("num connections   : " + status.getNumConnections());
        LOG.info("connect candidates: " + status.getConnectCandidates());
        LOG.info("announcing to DHT : " + status.announcingToDht());
        LOG.info("announcing to LSD : " + status.announcingToLsd());
        LOG.info("next announce in  : " + status.nextAnnounce() + " ms");
        LOG.info("================================================\n");

        if (peerInfos.size() == 0) {
            LOG.info("had no peers");
            LOG.info("forcing re-announcement.");
            torrentHandle.forceDHTAnnounce();

            //We'll check ourselves again after the interval and 1.5 minutes.
            Engine.instance().getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    long sleepForAnnounceCheck = 30000;
                    LOG.info("sleeping(sleepForAnnounceCheck = " + sleepForAnnounceCheck+")...");
                    SystemClock.sleep(sleepForAnnounceCheck);
                    forceDHTAnnounceIfNoPeers(torrentHandle, listener);
                }
            });
        } else if (listener != null) {
            // if we have peers, we can remove this DHT Bootstrap listener.
            LOG.info("had peers removing listener.");
            BTEngine.getInstance().getSession().removeListener(listener);
        }
    }

    private static class DHTBootstrapListener implements AlertListener {
        private static final Logger LOG = Logger.getLogger(DHTBootstrapListener.class);
        private final TorrentHandle torrentHandle;
        private final int[] types = new int[] { AlertType.DHT_BOOTSTRAP.swig() };

        DHTBootstrapListener(TorrentHandle torrentHandle) {
            this.torrentHandle = torrentHandle;
        }

        @Override
        public int[] types() {
            return types;
        }

        @Override
        public void alert(Alert<?> alert) {
            if (types[0] == alert.type().swig()) {
                LOG.info("received DHT_BOOTSTRAP signal.");
                DhtBootstrapAlert bootstrapAlert = (DhtBootstrapAlert) alert;
                LOG.info("what: " + bootstrapAlert.what());
                LOG.info("message: " + bootstrapAlert.message());

                // IDEA: We could re-announce every torrent we already have if they didn't have peers.
                // BTEngine.getInstance().getSession().getTorrents() : [<TorrentHandle>]
                forceDHTAnnounceIfNoPeers(torrentHandle, this);
            }
        }
    }

    // important to keep class public so it can be instantiated when the dialog is re-created on orientation changes.
    public static class ShowNoWifiInformationDialog extends AbstractDialog {
        public static ShowNoWifiInformationDialog newInstance() {
             return new ShowNoWifiInformationDialog();
        }

        // Important to keep this guy 'public', even if IntelliJ thinks you shouldn't.
        // otherwise, the app crashes when you turn the screen and the dialog can't
        public ShowNoWifiInformationDialog() {
            super(R.layout.dialog_default_info);
        }

        @Override
        protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
            TextView title = findView(dlg, R.id.dialog_default_info_title);
            title.setText(R.string.wifi_network_unavailable);
            TextView text = findView(dlg, R.id.dialog_default_info_text);
            text.setText(R.string.according_to_settings_i_cant_seed_unless_wifi);

            Button okButton = findView(dlg, R.id.dialog_default_info_button_ok);
            okButton.setText(android.R.string.ok);
            okButton.setOnClickListener(new OkButtonOnClickListener(dlg));
        }
    }

    private static class OkButtonOnClickListener implements View.OnClickListener {
        private final Dialog newNoWifiInformationDialog;

        public OkButtonOnClickListener(Dialog newNoWifiInformationDialog) {
            this.newNoWifiInformationDialog = newNoWifiInformationDialog;
        }

        @Override
        public void onClick(View view) {
            newNoWifiInformationDialog.dismiss();
        }
    }
}