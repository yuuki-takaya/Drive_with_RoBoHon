package com.example.tsukasa.robohon;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toolbar;

import java.util.List;
import java.util.Locale;

import com.example.tsukasa.robohon.customize.ScenarioDefinitions;
import com.example.tsukasa.robohon.util.VoiceUIManagerUtil;
import com.example.tsukasa.robohon.util.VoiceUIVariableUtil.VoiceUIVariableListHelper;

import jp.co.sharp.android.rb.projectormanager.ProjectorManagerServiceUtil;
import jp.co.sharp.android.voiceui.VoiceUIManager;
import jp.co.sharp.android.voiceui.VoiceUIVariable;
import jp.co.sharp.android.rb.camera.FaceDetectionUtil;
import jp.co.sharp.android.rb.camera.ShootMediaUtil;
import jp.co.sharp.android.rb.rbdance.DanceUtil;


public class MainActivity extends Activity implements MainActivityVoiceUIListener.MainActivityScenarioCallback {
    public static final String TAG = MainActivity.class.getSimpleName();
    /**
     * 顔認識結果通知Action定義.
     */
    public static final String ACTION_RESULT_FACE_DETECTION = "com.example.tsukasa.robohon.action.RESULT_FACE_DETECTION";
    /**
     * 写真/動画撮影結果通知Action定義.
     */
    public static final String ACTION_RESULT_TAKE_PICTURE = "com.example.tsukasa.robohon.action.RESULT_TAKE_PICTURE";
    /**
     * 動画撮影結果通知Action定義.
     */
    public static final String ACTION_RESULT_REC_MOVIE = "com.example.tsukasa.robohon.action.RESULT_REC_MOVIE";
    /**
     * ダンス実行結果通知用Action定義.
     */
    public static final String ACTION_RESULT_DANCE = "com.example.tsukasa.robohon.action.RESULT_DANCE";
    /**
     * 音声UI制御.
     */
    private VoiceUIManager mVoiceUIManager = null;
    /**
     * 音声UIイベントリスナー.
     */
    private MainActivityVoiceUIListener mMainActivityVoiceUIListener = null;
    /**
     * 音声UIの再起動イベント検知.
     */
    private VoiceUIStartReceiver mVoiceUIStartReceiver = null;
    /**
     * ホームボタンイベント検知.
     */
    private HomeEventReceiver mHomeEventReceiver;
    /**
     * プロジェクター状態変化イベント検知.
     */
    private ProjectorEventReceiver mProjectorEventReceiver;
    /**
     * プロジェクタ照射中のWakelock.
     */
    private android.os.PowerManager.WakeLock mWakelock;
    /**
     * 排他制御用.
     */
    private Object mLock = new Object();
    /**
     * プロジェクタ照射状態.
     */
    private boolean isProjected = false;
    /**
     * カメラ結果取得用.
     */
    private CameraResultReceiver mCameraResultReceiver;
    /**
     * ダンス実行結果取得用.
     */
    private DanceResultReceiver mDanceResultReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        setContentView(R.layout.activity_main);

        //タイトルバー設定.
        setupTitleBar();

        //ホームボタンの検知登録.
        mHomeEventReceiver = new HomeEventReceiver();
        IntentFilter filterHome = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mHomeEventReceiver, filterHome);

        //VoiceUI再起動の検知登録.
        mVoiceUIStartReceiver = new VoiceUIStartReceiver();
        IntentFilter filter = new IntentFilter(VoiceUIManager.ACTION_VOICEUI_SERVICE_STARTED);
        registerReceiver(mVoiceUIStartReceiver, filter);

        //TODO プロジェクタイベントの検知登録(プロジェクター利用時のみ).
        //setProjectorEventReceiver();

        //TODO カメラ連携起動結果取得用レシーバー登録(カメラ利用時のみ).
        //mCameraResultReceiver = new CameraResultReceiver();
        //IntentFilter filterCamera = new IntentFilter(ACTION_RESULT_TAKE_PICTURE);
        //filterCamera.addAction(ACTION_RESULT_REC_MOVIE);
        //filterCamera.addAction(ACTION_RESULT_FACE_DETECTION);
        //registerReceiver(mCameraResultReceiver, filterCamera);

        //TODO ダンス連携起動結果取得用レシーバー登録(ダンス利用時のみ).
        //mDanceResultReceiver = new DanceResultReceiver();
        //IntentFilter filterDance = new IntentFilter(ACTION_RESULT_DANCE);
        //registerReceiver(mDanceResultReceiver, filterDance);


        //発話ボタンの実装.
        Button Button = (Button) findViewById(R.id.accost);
        Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mVoiceUIManager != null) {
                    VoiceUIVariableListHelper helper = new VoiceUIVariableListHelper().addAccost(ScenarioDefinitions.ACC_Judge);
                    VoiceUIManagerUtil.updateAppInfo(mVoiceUIManager, helper.getVariableList(), true);
                }
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        Log.v(TAG, "onResume()");

        //VoiceUIManagerのインスタンス取得.
        if (mVoiceUIManager == null) {
            mVoiceUIManager = VoiceUIManager.getService(getApplicationContext());
        }
        //MainActivityVoiceUIListener生成.
        if (mMainActivityVoiceUIListener == null) {
            mMainActivityVoiceUIListener = new MainActivityVoiceUIListener(this);
        }
        //VoiceUIListenerの登録.
        VoiceUIManagerUtil.registerVoiceUIListener(mVoiceUIManager, mMainActivityVoiceUIListener);

        //Scene有効化.
        VoiceUIManagerUtil.enableScene(mVoiceUIManager, ScenarioDefinitions.SCENE_COMMON);
        VoiceUIManagerUtil.enableScene(mVoiceUIManager, ScenarioDefinitions.JudgeMode);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "onPause()");

        //バックに回ったら発話を中止する.
        VoiceUIManagerUtil.stopSpeech();

        //VoiceUIListenerの解除.
        VoiceUIManagerUtil.unregisterVoiceUIListener(mVoiceUIManager, mMainActivityVoiceUIListener);

        //Scene無効化.
        VoiceUIManagerUtil.disableScene(mVoiceUIManager, ScenarioDefinitions.SCENE_COMMON);
        VoiceUIManagerUtil.disableScene(mVoiceUIManager, ScenarioDefinitions.JudgeMode);

        //デフォルトの言語設定に戻す
        Locale locale = Locale.getDefault();
        VoiceUIManagerUtil.setAsr(mVoiceUIManager, locale);
        VoiceUIManagerUtil.setTts(mVoiceUIManager, locale);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy()");

        //ホームボタンの検知破棄.
        this.unregisterReceiver(mHomeEventReceiver);

        //VoiceUI再起動の検知破棄.
        this.unregisterReceiver(mVoiceUIStartReceiver);

        //TODO プロジェクタイベントの検知破棄(プロジェクター利用時のみ).
        //this.unregisterReceiver(mProjectorEventReceiver);

        //TODO カメラ連携起動結果取得用レシーバー破棄(カメラ利用時のみ).
        //this.unregisterReceiver(mCameraResultReceiver);

        //TODO ダンス結果用レシーバーの破棄(ダンス利用時のみ).
        //this.unregisterReceiver(mDanceResultReceiver);

        //インスタンスのごみ掃除.
        mVoiceUIManager = null;
        mMainActivityVoiceUIListener = null;
        mProjectorEventReceiver = null;
    }

    /**
     * VoiceUIListenerクラスからのコールバックを実装する.
     */
    @Override
    public void onExecCommand(String command, List<VoiceUIVariable> variables) {
        Log.v(TAG, "onExecCommand() : " + command);
        switch (command) {
            case ScenarioDefinitions.FUNC_END_APP:
                finish();
                break;
            case ScenarioDefinitions.FUNC_START_PROJECTOR:
                //TODO プロジェクタマネージャの開始(プロジェクター利用時のみ).
                //if(!isProjected) {
                //    startService(getIntentForProjector());
                //}
                break;
            default:
                break;
        }
    }

    /**
     * タイトルバーを設定する.
     */
    private void setupTitleBar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);
    }

    /**
     * プロジェクターマネージャーの開始/停止用のIntentを設定する.
     */
    private Intent getIntentForProjector() {
        Intent intent = new Intent();
        ComponentName componentName = new ComponentName(
                ProjectorManagerServiceUtil.PACKAGE_NAME,
                ProjectorManagerServiceUtil.CLASS_NAME);
        //逆方向で照射する
        intent.putExtra(ProjectorManagerServiceUtil.EXTRA_PROJECTOR_OUTPUT, ProjectorManagerServiceUtil.EXTRA_PROJECTOR_OUTPUT_VAL_REVERSE);
        //足元に照射する
        intent.putExtra(ProjectorManagerServiceUtil.EXTRA_PROJECTOR_DIRECTION, ProjectorManagerServiceUtil.EXTRA_PROJECTOR_DIRECTION_VAL_UNDER);
        intent.setComponent(componentName);
        return intent;
    }

    /**
     * プロジェクターの状態変化イベントを受け取るためのレシーバーをセットする.
     */
    private void setProjectorEventReceiver() {
        Log.v(TAG, "setProjectorEventReceiver()");
        if (mProjectorEventReceiver == null) {
            mProjectorEventReceiver = new ProjectorEventReceiver();
        } else {
            return;
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_PREPARE);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_START);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_PAUSE);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_RESUME);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_END);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_END_ERROR);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_END_FATAL_ERROR);
        intentFilter.addAction(ProjectorManagerServiceUtil.ACTION_PROJECTOR_TERMINATE);
        registerReceiver(mProjectorEventReceiver, intentFilter);
    }

    /**
     * WakeLockを取得する.
     */
    private void acquireWakeLock() {
        Log.v(TAG, "acquireWakeLock()");
        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        synchronized (mLock) {
            if (mWakelock == null || !mWakelock.isHeld()) {
                mWakelock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP
                        | PowerManager.ON_AFTER_RELEASE, MainActivity.class.getName());
                mWakelock.acquire();
            }
        }
    }

    /**
     * WakeLockを開放する.
     */
    private void releaseWakeLock() {
        Log.v(TAG, "releaseWakeLock()");
        synchronized (mLock) {
            if (mWakelock != null && mWakelock.isHeld()) {
                mWakelock.release();
                mWakelock = null;
            }
        }
    }

    /**
     * 顔検出実行用インテント取得関数
     *
     * @param swing String型でTRUE or FALSE
     * @return 顔検出実行用intent
     */
    private Intent getIntentForFaceDetection(String swing) {
        Intent intent = new Intent(FaceDetectionUtil.ACTION_FACE_DETECTION_MODE);
        intent.setPackage(FaceDetectionUtil.PACKAGE);
        intent.putExtra(FaceDetectionUtil.EXTRA_REPLYTO_ACTION, ACTION_RESULT_FACE_DETECTION);
        intent.putExtra(FaceDetectionUtil.EXTRA_REPLYTO_PKG, getPackageName());
        intent.putExtra(FaceDetectionUtil.EXTRA_FACE_DETECTION_LENGTH, FaceDetectionUtil.EXTRA_FACE_DETECTION_LENGTH_NORMAL);
        intent.putExtra(FaceDetectionUtil.EXTRA_MOVE_HEAD, swing);
        return intent;
    }

    /**
     * 写真撮影実行用インテント取得関数
     *
     * @param facedetect boolean型
     * @return 写真撮影実行用intent
     */
    private Intent getIntentForPhoto(boolean facedetect) {
        Intent intent = new Intent(ShootMediaUtil.ACTION_SHOOT_IMAGE);
        intent.setPackage(ShootMediaUtil.PACKAGE);
        intent.putExtra(ShootMediaUtil.EXTRA_FACE_DETECTION, facedetect);
        intent.putExtra(ShootMediaUtil.EXTRA_REPLYTO_ACTION, ACTION_RESULT_TAKE_PICTURE);
        intent.putExtra(ShootMediaUtil.EXTRA_REPLYTO_PKG, getPackageName());
        //TODO 撮影対象指定する場合はContactIDを指定
        //intent.putExtra(ShootMediaUtil.EXTRA_CONTACTID, ShootMediaUtil.EXTRA_CONTACTID_OWNER);
        return intent;
    }

    /**
     * 動画撮影実行用インテント取得関数
     *
     * @param time int型(sec)
     * @return 動画撮影実行用intent
     */
    private Intent getIntentForVideo(int time) {
        Intent intent = new Intent(ShootMediaUtil.ACTION_SHOOT_MOVIE);
        intent.setPackage(ShootMediaUtil.PACKAGE);
        intent.putExtra(ShootMediaUtil.EXTRA_MOVIE_LENGTH, time);
        intent.putExtra(ShootMediaUtil.EXTRA_REPLYTO_ACTION, ACTION_RESULT_REC_MOVIE);
        intent.putExtra(ShootMediaUtil.EXTRA_REPLYTO_PKG, getPackageName());
        return intent;
    }

    /**
     * ダンス開始用のIntentを設定する.
     */
    private Intent getIntentForDance(String type) {
        Intent intent = new Intent(DanceUtil.ACTION_REQUEST_DANCE);
        intent.putExtra(DanceUtil.EXTRA_REPLYTO_ACTION, ACTION_RESULT_DANCE);
        intent.putExtra(DanceUtil.EXTRA_REPLYTO_PKG, getPackageName());
        intent.putExtra(DanceUtil.EXTRA_TYPE, type);
        if (type.equals(DanceUtil.EXTRA_TYPE_ASSIGN)) {
            intent.putExtra(DanceUtil.EXTRA_REQUEST_ID, 1);
        }
        return intent;
    }

    /**
     * ホームボタンの押下イベントを受け取るためのBroadcastレシーバークラス.<br>
     * <p/>
     * アプリは必ずホームボタンで終了する..
     */
    private class HomeEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "Receive Home button pressed");
            // ホームボタン押下でアプリ終了する.
            finish();
        }
    }

    /**
     * 音声UI再起動イベントを受け取るためのBroadcastレシーバークラス.<br>
     * <p/>
     * 稀に音声UIのServiceが再起動することがあり、その場合アプリはVoiceUIの再取得とListenerの再登録をする.
     */
    private class VoiceUIStartReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (VoiceUIManager.ACTION_VOICEUI_SERVICE_STARTED.equals(action)) {
                Log.d(TAG, "VoiceUIStartReceiver#onReceive():VOICEUI_SERVICE_STARTED");
                //VoiceUIManagerのインスタンス取得.
                mVoiceUIManager = VoiceUIManager.getService(getApplicationContext());
                if (mMainActivityVoiceUIListener == null) {
                    mMainActivityVoiceUIListener = new MainActivityVoiceUIListener(getApplicationContext());
                }
                //VoiceUIListenerの登録.
                VoiceUIManagerUtil.registerVoiceUIListener(mVoiceUIManager, mMainActivityVoiceUIListener);
            }
        }
    }

    /**
     * プロジェクターの状態変化時のイベントを受け取るためのBroadcastレシーバークラス.<br>
     * <p/>
     * 照射開始時にはWakeLockの取得、終了時にはWakeLockの開放する.<br>
     * アプリ仕様に応じて必要な処理があれば実装すること.
     */
    private class ProjectorEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "ProjectorEventReceiver#onReceive():" + intent.getAction());
            switch (intent.getAction()) {
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_PREPARE:
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_PAUSE:
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_RESUME:
                    break;
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_START:
                    acquireWakeLock();
                    isProjected = true;
                    break;
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_END:
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_END_FATAL_ERROR:
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_END_ERROR:
                case ProjectorManagerServiceUtil.ACTION_PROJECTOR_TERMINATE:
                    releaseWakeLock();
                    isProjected = false;
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * カメラ連携の結果を受け取るためのBroadcastレシーバー クラス.<br>
     * <p/>
     * それぞれの結果毎に処理を行う.
     */
    private class CameraResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "CameraResultReceiver#onReceive() : " + action);
            switch (action) {
                case ACTION_RESULT_FACE_DETECTION:
                    int result = intent.getIntExtra(FaceDetectionUtil.EXTRA_RESULT_CODE, FaceDetectionUtil.RESULT_CANCELED);
                    break;
                case ACTION_RESULT_TAKE_PICTURE:
                    result = intent.getIntExtra(ShootMediaUtil.EXTRA_RESULT_CODE, ShootMediaUtil.RESULT_CANCELED);
                    break;
                case ACTION_RESULT_REC_MOVIE:
                    result = intent.getIntExtra(ShootMediaUtil.EXTRA_RESULT_CODE, ShootMediaUtil.RESULT_CANCELED);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * ダンス実行結果を受け取るためのBroadcastレシーバー クラス.<br>
     * <p/>
     */
    private class DanceResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int result = intent.getIntExtra(DanceUtil.EXTRA_RESULT_CODE, DanceUtil.RESULT_CANCELED);
            if (result == DanceUtil.RESULT_OK) {
                // 正常に完了した場合.
                int id = intent.getIntExtra(DanceUtil.EXTRA_RESULT_ID, -1);
                String name = intent.getStringExtra(DanceUtil.EXTRA_RESULT_NAME);
            }
        }
    }
}
