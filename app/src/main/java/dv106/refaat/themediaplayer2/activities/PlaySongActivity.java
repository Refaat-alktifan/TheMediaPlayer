package dv106.refaat.themediaplayer2.activities;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ScaleDrawable;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

import dv106.refaat.themediaplayer2.R;
import dv106.refaat.themediaplayer2.database.DataSource;
import dv106.refaat.themediaplayer2.dialogfragments.AddSongToPlaylistDialogFragment;
import dv106.refaat.themediaplayer2.pojo.Song;
import dv106.refaat.themediaplayer2.preferences.PreferencesActivity;
import dv106.refaat.themediaplayer2.service.SongService;
import dv106.refaat.themediaplayer2.service.SongService.SongServiceBinder;
import dv106.refaat.themediaplayer2.util.DBCleaner;
import dv106.refaat.themediaplayer2.util.MediaPlayerTimeUtil;

public class PlaySongActivity extends FragmentActivity implements OnClickListener,
        OnSeekBarChangeListener, AddSongToPlaylistDialogFragment.AddSongToPlaylistDialogListener {

    private static final float VISUALIZER_HEIGHT_DIP = 50f;
    private Visualizer mVisualizer;
    private VisualizerView mVisualizerView;
    private LinearLayout mLinearLayout;

    private TextView tvArtist;
    private TextView tvTitle;
    private TextView tvCurrentProgress;
    private TextView tvTotalProgress;
    private TextView tvRepeat;
    private TextView tvShuffle;
    private SeekBar sbProgress;
    private ImageButton btnPrevious;
    private ImageButton btnPlayPause;
    private ImageButton btnNext;
    private ImageButton btnShuffle;
    private ImageButton btnRepeat;
    private ServiceConnection connection;
    private SongService service = null;
    private boolean bound;
    private Bundle extras;
    private ArrayList<Song> allSongs;
    private DataSource datasource;
    private ArrayList<String> allSongPaths;
    private String playlist;
    private BroadcastReceiver playSongBroadcastReceiver;
    private static CountDownTimer sbCountDown;
    private boolean restored;
    private TextView tvPlaylist;
    private AdView mAdView;
    private InterstitialAd interstitial;

    private void displayInterstitial() {
        if (interstitial.isLoaded()) {
            interstitial.show();

        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_song);


        AdRequest adRequest1 = new AdRequest.Builder().build();
        interstitial = new InterstitialAd(PlaySongActivity.this);
        interstitial.setAdUnitId("ca-app-pub-8493173606515444/6527547509");
        interstitial.loadAd(adRequest1);
        interstitial.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                displayInterstitial();
            }
        });



        MobileAds.initialize(getApplicationContext(),
                "ca-app-pub-8493173606515444/1331061144");



        mAdView = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);




        extras = getIntent().getExtras();
        if (extras != null)
            playlist = extras.getString("playlist");
        setUpComponents();
        setUpTheme();
        connectToService();
        getSongsFromDatabase();
        /* Start the service */
        Intent startIntent = new Intent(this, SongService.class);
        startService(startIntent);
        this.bindService(startIntent, connection, Context.BIND_AUTO_CREATE);

        mLinearLayout = (LinearLayout) findViewById(R.id.LayoutViewVisualizer);
        mVisualizerView = new VisualizerView(this);
        mVisualizerView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (VISUALIZER_HEIGHT_DIP * getResources()
                        .getDisplayMetrics().density)));
        mLinearLayout.addView(mVisualizerView);
        if (savedInstanceState != null) {
            restored = savedInstanceState.getBoolean("restored");
        }
    }

    private void setUpComponents() {
        tvArtist = (TextView) findViewById(R.id.tvPlaySongArtist);
        tvTitle = (TextView) findViewById(R.id.tvPlaySongTitle);
        tvCurrentProgress = (TextView) findViewById(R.id.tvPlaySongCurrentTime);
        tvTotalProgress = (TextView) findViewById(R.id.tvPlaySongTotalTime);
        tvRepeat = (TextView) findViewById(R.id.tvPlaySongRepeat);
        tvShuffle = (TextView) findViewById(R.id.tvPlaySongShuffle);
        sbProgress = (SeekBar) findViewById(R.id.sbPlaySongSeekBar);
        btnPrevious = (ImageButton) findViewById(R.id.btnPlaySongPrevious);
        btnPlayPause = (ImageButton) findViewById(R.id.btnPlaySongPlayPause);
        btnNext = (ImageButton) findViewById(R.id.btnPlaySongNext);
        btnShuffle = (ImageButton) findViewById(R.id.btnPlaySongShuffle);
        btnRepeat = (ImageButton) findViewById(R.id.btnPlaySongRepeat);
        tvPlaylist = (TextView) findViewById(R.id.tvPlaySongPlaylist);
        allSongPaths = new ArrayList<String>();

        btnPrevious.setOnClickListener(this);
        btnPlayPause.setOnClickListener(this);
        btnNext.setOnClickListener(this);
        btnShuffle.setOnClickListener(this);
        btnRepeat.setOnClickListener(this);
        sbProgress.setOnSeekBarChangeListener(this);
    }

    private void setUpTheme() {
        SharedPreferences sharedPref = PreferenceManager
                .getDefaultSharedPreferences(this);
        int txtColor = sharedPref.getInt(
                PreferencesActivity.KEY_PREF_TXT_COLOR, 1);

        tvArtist.setTextColor(txtColor);
        tvCurrentProgress.setTextColor(txtColor);
        tvPlaylist.setTextColor(txtColor);
        tvRepeat.setTextColor(txtColor);
        tvShuffle.setTextColor(txtColor);
        tvTitle.setTextColor(txtColor);
        tvTotalProgress.setTextColor(txtColor);
        setProgressBarColor(sbProgress, txtColor);
    }

    public void setProgressBarColor(ProgressBar progressBar, int newColor) {
        LayerDrawable ld = (LayerDrawable) progressBar.getProgressDrawable().getCurrent();
        ScaleDrawable d1 = (ScaleDrawable) ld.findDrawableByLayerId(android.R.id.progress);
        d1.setColorFilter(newColor, PorterDuff.Mode.SRC_IN);
        sbProgress.getThumb().setColorFilter(newColor, PorterDuff.Mode.SRC_IN);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("restored", true);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {

        if (service != null && !service.isPlaying()) {
            btnPlayPause.setImageDrawable(getResources().getDrawable(
                    R.drawable.ic_action_play_over_video));
        }
        if (mVisualizer != null) {
            mVisualizer.setEnabled(true);
        }
        if (tvRepeat != null) {
            SharedPreferences pref = PreferenceManager
                    .getDefaultSharedPreferences(this);
            if (pref.getBoolean("isRepeat", false)) {
                tvRepeat.setText(getResources().getString(R.string.repeat));
            } else {
                tvRepeat.setText("");
            }
        }
        if (tvShuffle != null) {
            SharedPreferences pref = PreferenceManager
                    .getDefaultSharedPreferences(this);
            if (pref.getBoolean("isShuffle", false)) {
                tvShuffle.setText(getResources().getString(R.string.shuffle));
            } else {
                tvShuffle.setText("");
            }
        }

        if(service != null && service.isPlaying()){
            initSeekBar(service.getCurrentPosition());
        }

        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_play_song, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_play_song_share:
                Song tmpSong = null;
                try {
                    datasource.open();
                    tmpSong = datasource.getSong(service.getCurrentSongPath());
                    datasource.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                if (tmpSong != null) {
                    Intent i = new Intent(android.content.Intent.ACTION_SEND);
                    Uri uri = Uri.parse(tmpSong.getDATA());
                    String songTitle = tmpSong.getTITLE();
                    String songArtist = tmpSong.getARTIST();
                    i.setType("audio/*");
                    i.putExtra(Intent.EXTRA_STREAM, uri);
                    i.putExtra(
                            Intent.EXTRA_TEXT,
                            getResources().getString(R.string.share_audio_msg_one)
                                    + " "
                                    + songTitle
                                    + " "
                                    + getResources().getString(
                                    R.string.share_audio_msg_two) + " "
                                    + songArtist);

                    startActivity(Intent.createChooser(i,
                            getResources().getString(R.string.share_audio_title)));
                }
                break;

            case R.id.menu_play_song_add_to_playlist:
                ArrayList<String> allPlaylists = null;
                try {
                    datasource.open();
                    allPlaylists = datasource.getAllPlaylists();
                    datasource.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                if (allPlaylists != null && allPlaylists.size() != 0) {
                    String songPath = service.getCurrentSongPath();
                    AddSongToPlaylistDialogFragment dialog = new AddSongToPlaylistDialogFragment();
                    Bundle bundle = new Bundle();
                    bundle.putString("songPath", songPath);
                    dialog.setArguments(bundle);
                    dialog.show(getSupportFragmentManager(), "dialog");
                } else {
                    Toast.makeText(this, getResources().getString(R.string.add_new_playlist_dialog_fragment_title), Toast.LENGTH_SHORT).show();
                }
                break;

        }
        return super.onOptionsItemSelected(item);
    }

    private void getSongsFromDatabase() {
        datasource = new DataSource(this);
        /*
         * play from the playlist if that has been specified, else play all
		 * songs
		 */
        SharedPreferences settings = getSharedPreferences("play_mode_prefs", 0);
        if (settings.getBoolean("from_playlist", false)) {
            this.playlist = settings.getString("playlist_name", "");
        } else {
            this.playlist = null;
        }
        if (playlist != null) {
            try {
                datasource.open();
                allSongs = datasource.getSongsFromPlaylist(playlist);
                datasource.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            try {
                datasource.open();
                allSongs = datasource.getAllSongs();
                datasource.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        for (Song song : allSongs) {
            allSongPaths.add(song.getDATA());
        }
    }

    private void connectToService() {
        connection = new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName cName, IBinder binder) {
                SongServiceBinder songBinder = (SongServiceBinder) binder;
                service = songBinder.getService();
                bound = true;
                if (extras != null) {
                    if (extras.getBoolean("resumePlaySongActivity")) {
                        resumePlaybackActivity();
                    } else {
                        playSong(extras);
                    }
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName cName) {
                service = null;
                bound = false;
            }
        };
    }

    private void resumePlaybackActivity() {
        String songPath = service.getCurrentSongPath();
        SharedPreferences settings = getSharedPreferences("play_mode_prefs", 0);
        if (settings.getBoolean("from_playlist", false)) {
            this.playlist = settings.getString("playlist_name", "");
            tvPlaylist.setText(getResources().getString(
                    R.string.play_song_activity_playlist)
                    + " " + playlist);
        } else {
            this.playlist = null;
            tvPlaylist.setText("");
        }
        Song tmpSong = null;
        try {
            datasource.open();
            tmpSong = datasource.getSong(songPath);
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (tmpSong != null) {
            String artist = tmpSong.getARTIST();
            String title = tmpSong.getTITLE();
            String duration = MediaPlayerTimeUtil.formatMillisecond(Long
                    .valueOf(tmpSong.getDURATION()));
            initSeekBar(service.getCurrentPosition());
            setupVisualizerFx();
            if (mVisualizer != null)
                mVisualizer.setEnabled(true);
            tvArtist.setText(artist);
            tvTitle.setText(title);
            tvTotalProgress.setText(duration);
        }

    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(
                playSongBroadcastReceiver);
        unbindService(connection);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        if (mVisualizer != null)
            mVisualizer.setEnabled(false);

        if(sbCountDown != null){
            sbCountDown.cancel();
        }
        super.onPause();
    }

    /*
     * Initialize the SeekBar, starting a CountDownTimer that polls the service
     * for the current position of the MediaPlayer
     */
    private void initSeekBar(int currentTime) {
        int totalDur = service.getTotalDuration();

        sbProgress.setMax(totalDur);

        if (sbCountDown != null)
            sbCountDown.cancel();
        sbCountDown = new CountDownTimer(currentTime, 1000) {
            public void onTick(long millisUntilFinished) {
                System.out.println("tick");
                int pos = service.getCurrentPosition();
                if (pos != -1) {
                    String progress = MediaPlayerTimeUtil.formatMillisecond(pos);
                    tvCurrentProgress.setText(progress);
                    sbProgress.setProgress(pos);
                }
            }

            public void onFinish() {
                prepareNextSong();
            }

        }.start();
    }

    private void prepareNextSong() {
        // make it delayed so that the service has time to swap tracks
        int indexOfSong = allSongPaths.indexOf(service.getCurrentSongPath());
        if (indexOfSong != allSongPaths.size() - 1) {
            Handler mHandler = new Handler();
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    setupVisualizerFx();
                    if (mVisualizer != null)
                        mVisualizer.setEnabled(true);
                    tvTotalProgress.setText(MediaPlayerTimeUtil
                            .formatMillisecond(service.getTotalDuration()));
                    initSeekBar(service.getTotalDuration());
                    int indexOfSong = allSongPaths.indexOf(service
                            .getCurrentSongPath());
                    tvArtist.setText(allSongs.get(indexOfSong).getARTIST());
                    tvTitle.setText(allSongs.get(indexOfSong).getTITLE());
                }
            }, 1000);
        }
    }

    @Override
    public void onClick(View v) {
        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);
        switch (v.getId()) {
            case R.id.btnPlaySongPrevious:
                if (pref.getBoolean("isShuffle", false)) {
                    playRandomSong();
                } else {
                    playPreviousSong();
                }
                break;

            case R.id.btnPlaySongPlayPause:
                playOrPauseSong();
                break;

            case R.id.btnPlaySongNext:
                if (pref.getBoolean("isShuffle", false)) {
                    playRandomSong();
                } else {
                    playNextSong();
                }
                break;

            case R.id.btnPlaySongShuffle:
                setShuffle();
                break;

            case R.id.btnPlaySongRepeat:
                setRepeat();
                break;
        }
    }

    private void setRepeat() {
        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = pref.edit();
        boolean isShuffle = pref.getBoolean("isShuffle", false);
        boolean isRepeat = pref.getBoolean("isRepeat", false);
        if (isRepeat) {
            // Av-aktivera repeat
            tvRepeat.setText("");
            isRepeat = false;
        } else {
            // Aktivera repeat
            tvRepeat.setText(getResources().getString(R.string.repeat));
            isRepeat = true;
        }

        if (isShuffle) {
            // Av-aktivera shuffle
            isShuffle = false;
            tvShuffle.setText("");
        }
        editor.putBoolean("isRepeat", isRepeat);
        editor.putBoolean("isShuffle", isShuffle);
        editor.apply();
    }

    private void setShuffle() {
        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = pref.edit();
        boolean isShuffle = pref.getBoolean("isShuffle", false);
        boolean isRepeat = pref.getBoolean("isRepeat", false);
        if (isShuffle) {
            // Av-aktivera shuffle
            tvShuffle.setText("");
            isShuffle = false;
        } else {
            // Aktivera shuffle
            tvShuffle.setText(getResources().getString(R.string.shuffle));
            isShuffle = true;
        }

        if (isRepeat) {
            // Av-aktivera repeat
            isRepeat = false;
            tvRepeat.setText("");
        }
        editor.putBoolean("isRepeat", isRepeat);
        editor.putBoolean("isShuffle", isShuffle);
        editor.apply();
    }

	/*
     * PlaySongActivity commands to the SongService
	 */

    private void playRandomSong() {
        Random rand = new Random();
        int max = allSongPaths.size();
        if (max != 1) {
            int randomSongIndex = rand.nextInt(max);
            if (randomSongIndex != allSongPaths.indexOf(service
                    .getCurrentSongPath())) {
                String songPath = allSongPaths.get(randomSongIndex);
                File tmpFile = new File(songPath);
                if (tmpFile.exists()) {
                    service.playMusic(songPath, playlist);
                    initSeekBar(service.getTotalDuration());
                    String artist = allSongs.get(randomSongIndex).getARTIST();
                    String title = allSongs.get(randomSongIndex).getTITLE();
                    tvArtist.setText(artist);
                    tvTitle.setText(title);
                    int duration = service.getTotalDuration();
                    tvTotalProgress.setText(MediaPlayerTimeUtil
                            .formatMillisecond(duration));
                    setupVisualizerFx();
                    if (mVisualizer != null)
                        mVisualizer.setEnabled(true);
                    btnPlayPause.setImageDrawable(getResources().getDrawable(
                            R.drawable.ic_action_pause_over_video));
                } else {
                    DBCleaner cleaner = new DBCleaner();
                    cleaner.cleanIfNeeded(this);
                    try {
                        datasource.open();
                        if (playlist != null) {
                            allSongs = datasource.getSongsFromPlaylist(playlist);
                        } else {
                            allSongs = datasource.getAllSongs();
                        }
                        datasource.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    allSongPaths.clear();
                    for (Song song : allSongs) {
                        allSongPaths.add(song.getDATA());
                    }
                    playPreviousSong();
                }
            } else {
                playRandomSong();
            }
        }
    }

    private void playSong(Bundle extras) {
        String songPath = extras.getString("songPath");
        String artist = extras.getString("artist");
        String title = extras.getString("title");
        playlist = extras.getString("playlist");
        if (playlist != null) {
            tvPlaylist.setText(getResources().getString(
                    R.string.play_song_activity_playlist)
                    + " " + playlist);
        } else {
            tvPlaylist.setText(playlist);
        }
        long dur = Long.valueOf(extras.getString("duration"));
        String duration = MediaPlayerTimeUtil.formatMillisecond(dur);
        tvArtist.setText(artist);
        tvTitle.setText(title);
        tvTotalProgress.setText(duration);
        if (bound) {
            if (!restored) {
                service.playMusic(songPath, playlist);
            }
            initSeekBar(service.getTotalDuration());
            setupVisualizerFx();
            if (mVisualizer != null)
                mVisualizer.setEnabled(true);
        }
    }

    private void playPreviousSong() {
        int indexOfSong = allSongPaths.indexOf(service.getCurrentSongPath());
        if (indexOfSong != 0) {

            String songPath = allSongPaths.get(indexOfSong - 1);
            File tmpFile = new File(songPath);
            if (tmpFile.exists()) {
                service.playMusic(songPath, playlist);
                initSeekBar(service.getTotalDuration());
                String artist = allSongs.get(indexOfSong - 1).getARTIST();
                String title = allSongs.get(indexOfSong - 1).getTITLE();
                tvArtist.setText(artist);
                tvTitle.setText(title);
                int duration = service.getTotalDuration();
                tvTotalProgress.setText(MediaPlayerTimeUtil
                        .formatMillisecond(duration));
                setupVisualizerFx();
                if (mVisualizer != null)
                    mVisualizer.setEnabled(true);
                btnPlayPause.setImageDrawable(getResources().getDrawable(
                        R.drawable.ic_action_pause_over_video));
            } else {
                DBCleaner cleaner = new DBCleaner();
                cleaner.cleanIfNeeded(this);
                try {
                    datasource.open();
                    if (playlist != null) {
                        allSongs = datasource.getSongsFromPlaylist(playlist);
                    } else {
                        allSongs = datasource.getAllSongs();
                    }
                    datasource.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                allSongPaths.clear();
                for (Song song : allSongs) {
                    allSongPaths.add(song.getDATA());
                }
                playPreviousSong();
            }

        }
    }

    private void playOrPauseSong() {
        int state = service.pauseOrResumeMusic();
        if (state == 0) {
            btnPlayPause.setImageDrawable(getResources().getDrawable(
                    R.drawable.ic_action_play_over_video));
            if(sbCountDown != null){
                sbCountDown.cancel();
            }
        } else if (state == 1) {
            btnPlayPause.setImageDrawable(getResources().getDrawable(
                    R.drawable.ic_action_pause_over_video));
            if(service != null){
                initSeekBar(service.getCurrentPosition());
            }
        }
    }

    private void playNextSong() {
        int indexOfSong = allSongPaths.indexOf(service.getCurrentSongPath());
        if (indexOfSong != allSongPaths.size() - 1) {
            String songPath = allSongPaths.get(indexOfSong + 1);
            File tmpFile = new File(songPath);
            if (tmpFile.exists()) {
                service.playMusic(songPath, playlist);
                initSeekBar(service.getTotalDuration());
                String artist = allSongs.get(indexOfSong + 1).getARTIST();
                String title = allSongs.get(indexOfSong + 1).getTITLE();
                tvArtist.setText(artist);
                tvTitle.setText(title);
                int duration = service.getTotalDuration();
                tvTotalProgress.setText(MediaPlayerTimeUtil
                        .formatMillisecond(duration));
                setupVisualizerFx();
                if (mVisualizer != null)
                    mVisualizer.setEnabled(true);
                btnPlayPause.setImageDrawable(getResources().getDrawable(
                        R.drawable.ic_action_pause_over_video));
            } else {
                DBCleaner cleaner = new DBCleaner();
                cleaner.cleanIfNeeded(this);
                try {
                    datasource.open();
                    if (playlist != null) {
                        allSongs = datasource.getSongsFromPlaylist(playlist);
                    } else {
                        allSongs = datasource.getAllSongs();
                    }
                    datasource.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                allSongPaths.clear();
                for (Song song : allSongs) {
                    allSongPaths.add(song.getDATA());
                }
                playNextSong();
            }
        }
    }

    /*
     * Callback methods for the SeekBar
     */
    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        int position = seekBar.getProgress();
        service.seekToPosition(position);
        initSeekBar(service.getRemainingTime());
        btnPlayPause.setImageDrawable(getResources().getDrawable(
                R.drawable.ic_action_pause_over_video));
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress,
                                  boolean fromUser) {
        if (fromUser) {
            tvCurrentProgress.setText(MediaPlayerTimeUtil
                    .formatMillisecond(progress));
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        sbCountDown.cancel();

    }

    private void setupVisualizerFx() {
        SharedPreferences sharedPref = PreferenceManager
                .getDefaultSharedPreferences(this);
        boolean musicAnimationEnabled = sharedPref.getBoolean(
                PreferencesActivity.KEY_PREF_MUSIC_ANIMATION, true);
        if (musicAnimationEnabled) {
            int audioSessionId = service.getAudioSessionId();
            if (mVisualizer != null) {
                mVisualizer.setEnabled(false);
            }
            mVisualizer = new Visualizer(audioSessionId);
            mVisualizer.setEnabled(false);
            try {
                mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
            mVisualizer.setDataCaptureListener(
                    new Visualizer.OnDataCaptureListener() {
                        public void onWaveFormDataCapture(
                                Visualizer visualizer, byte[] bytes,
                                int samplingRate) {
                            mVisualizerView.updateVisualizer(bytes);
                        }

                        public void onFftDataCapture(Visualizer visualizer,
                                                     byte[] bytes, int samplingRate) {
                        }
                    }, Visualizer.getMaxCaptureRate() / 2, true, false);
        }

    }

    @Override
    public void onAddSongToPlaylistDialogPositiveClick(String playlistName, boolean success) {
        Toast.makeText(this, getResources().getString(R.string.add_track_to_playlist_dialog_fragment_feedback_added)
                + " " + getResources().getString(R.string.add_track_to_playlist_dialog_fragment_feedback_to) + " " + playlistName, Toast.LENGTH_SHORT).show();
    }

    /**
     * A simple class that draws waveform data received from a
     * {@link Visualizer.OnDataCaptureListener#onWaveFormDataCapture }
     */
    class VisualizerView extends View {
        private byte[] mBytes;
        private float[] mPoints;
        private Rect mRect = new Rect();

        private Paint mForePaint = new Paint();

        public VisualizerView(Context context) {
            super(context);
            init();

        }

        private void init() {
            mBytes = null;

            SharedPreferences sharedPref = PreferenceManager
                    .getDefaultSharedPreferences(getContext());
            int txtColor = sharedPref.getInt(
                    PreferencesActivity.KEY_PREF_TXT_COLOR, 1);

            mForePaint.setStrokeWidth(1f);
            mForePaint.setAntiAlias(true);
            mForePaint.setColor(txtColor);
        }

        public void updateVisualizer(byte[] bytes) {
            mBytes = bytes;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (mBytes == null) {
                return;
            }

            if (mPoints == null || mPoints.length < mBytes.length * 4) {
                mPoints = new float[mBytes.length * 4];
            }

            mRect.set(0, 0, getWidth(), getHeight());

            for (int i = 0; i < mBytes.length - 1; i++) {
                mPoints[i * 4] = mRect.width() * i / (mBytes.length - 1);
                mPoints[i * 4 + 1] = mRect.height() / 2
                        + ((byte) (mBytes[i] + 128)) * (mRect.height() / 2)
                        / 128;
                mPoints[i * 4 + 2] = mRect.width() * (i + 1)
                        / (mBytes.length - 1);
                mPoints[i * 4 + 3] = mRect.height() / 2
                        + ((byte) (mBytes[i + 1] + 128)) * (mRect.height() / 2)
                        / 128;
            }

            canvas.drawLines(mPoints, mForePaint);
        }
    }
}