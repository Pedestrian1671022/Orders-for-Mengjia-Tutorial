package com.example.pedestrian_username.ordersformengjia;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.GrammarListener;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.SynthesizerListener;

import org.json.JSONException;
import org.json.JSONObject;
import org.ros.android.RosActivity;
import org.ros.message.MessageListener;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import static com.iflytek.cloud.SpeechConstant.ENGINE_TYPE;
import static com.iflytek.cloud.SpeechConstant.KEY_REQUEST_FOCUS;
import static com.iflytek.cloud.SpeechConstant.PARAMS;
import static com.iflytek.cloud.SpeechConstant.PITCH;
import static com.iflytek.cloud.SpeechConstant.SPEED;
import static com.iflytek.cloud.SpeechConstant.STREAM_TYPE;
import static com.iflytek.cloud.SpeechConstant.TYPE_LOCAL;
import static com.iflytek.cloud.SpeechConstant.VOICE_NAME;
import static com.iflytek.cloud.SpeechConstant.VOLUME;

/**
 * @author damonkohler@google.com (Damon Kohler)
 */
public class MainActivity extends RosActivity {

    // 语音对象
    private com.iflytek.cloud.SpeechRecognizer mAsr;
    private com.iflytek.cloud.SpeechRecognizer mIat;
    private SpeechSynthesizer mTts;
    private Toast mToast;
    private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();

    // 本地语法文件
    private String mLocalGrammar = null;
    private String mContent;
    private String Text;
    private ListView listView;
    private List<ChatItem> list;
    private ChatAdapter chatAdapter;
    private boolean flag = true;
    private Button switch_button;
    private Button asr_iat_tts_socket_button;
    // 函数调用返回值
    private int ret = 0;
    private Talker talker;
    private Listener listener;

    public MainActivity() {
        super("Orders for Mengjia", "Orders for Mengjia");
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SpeechUtility.createUtility(MainActivity.this, SpeechConstant.APPID+"=581c7563");
        setContentView(R.layout.main_activity);

        mToast = Toast.makeText(this,null,Toast.LENGTH_SHORT);

        listView = (ListView) findViewById(R.id.listview);
        switch_button = (Button) findViewById(R.id.switch_button);
        asr_iat_tts_socket_button = (Button) findViewById(R.id.asr_iat_tts_socket);

        list = new ArrayList<ChatItem>();
        chatAdapter = new ChatAdapter(this, list);
        listView.setAdapter(chatAdapter);

        mAsr = com.iflytek.cloud.SpeechRecognizer.createRecognizer(MainActivity.this, mInitListener);
        mIat = com.iflytek.cloud.SpeechRecognizer.createRecognizer(MainActivity.this, mInitListener);
        mTts = SpeechSynthesizer.createSynthesizer(MainActivity.this, mInitListener);


        asr_iat_tts_socket_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ret = mAsr.startListening(asr_mRecognizerListener);
                        if (ret != ErrorCode.SUCCESS) {
                            mToast.setText("识别失败,错误码: " + ret);
                            mToast.show();
                        }
                    }
                }).start();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mIatResults.clear();
                        ret = mIat.startListening(iat_mRecognizerListener);
                        if (ret != ErrorCode.SUCCESS) {
                            mToast.setText("听写失败,错误码：" + ret);
                            mToast.show();
                        }
                    }
                }).start();
            }
        });

        switch_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(flag){
                    flag = !flag;
                    switch_button.setText(R.string.IAT);
                }
                else {
                    flag = !flag;
                    switch_button.setText(R.string.ASR);
                }
            }
        });

        asr_setParam();
        iat_setParam();
        tts_setParam();

        talker = new Talker("pc_remote_control", std_msgs.String._TYPE);

        listener = new Listener("android_remote_control", std_msgs.String._TYPE, new MessageListener<std_msgs.String>() {
            @Override
            public void onNewMessage(std_msgs.String message) {
                String str = message.getData();
                list.add(new ChatItem(ChatType.IN, BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher), str));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        chatAdapter.notifyDataSetChanged();
                        listView.invalidateViews();
                        listView.setSelection(list.size() - 1);
                    }
                });
                ret = mTts.startSpeaking(str, mTtsListener);
                if (ret != ErrorCode.SUCCESS) {
                    mToast.setText("合成失败,错误码: " + ret);
                    mToast.show();
                }
            }
        });
    }

    /**
     * 本地构建语法监听器。
     */
    private GrammarListener mLocalGrammarListener = new GrammarListener() {
        @Override
        public void onBuildFinish(String grammarId, SpeechError error) {
            if(error == null){
                mToast.setText("语法构建成功：" + grammarId);
                mToast.show();
            }else{
                mToast.setText("语法构建失败,错误码：" + error.getErrorCode());
                mToast.show();
            }
        }
    };

    /**
     * 初始化监听器。
     */
    private InitListener mInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            if (code != ErrorCode.SUCCESS) {
                mToast.setText("初始化失败,错误码："+code);
                mToast.show();
            }
        }
    };

    /**
     * 识别监听器。
     */
    private RecognizerListener asr_mRecognizerListener = new RecognizerListener() {

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            mToast.setText("当前正在说话，音量大小：" + volume);
            mToast.show();
        }

        @Override
        public void onResult(final RecognizerResult result, boolean isLast) {
            Text = result.getResultString();
            talker.publish(Text);
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            mToast.setText("语音识别结束");
            mToast.show();
        }

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            mToast.setText("语音识别开始");
            mToast.show();
        }

        @Override
        public void onError(SpeechError error) {
            mToast.setText("语音识别没有结果");
            mToast.show();
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }

    };

    /**
     * 听写监听器。
     */
    private RecognizerListener iat_mRecognizerListener = new RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            mToast.setText("语音听写开始");
            mToast.show();
        }

        @Override
        public void onError(SpeechError error) {
            mToast.setText("语音听写没有结果");
            mToast.show();
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            mToast.setText("语音听写结束");
            mToast.show();
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            String text = JsonParser.parseIatResult(results.getResultString());
            String sn = null;
            // 读取json结果中的sn字段
            try {
                JSONObject resultJson = new JSONObject(results.getResultString());
                sn = resultJson.optString("sn");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            mIatResults.put(sn, text);

            StringBuffer resultBuffer = new StringBuffer();
            for (String key : mIatResults.keySet()) {
                resultBuffer.append(mIatResults.get(key));
            }
            Text = resultBuffer.toString();
            talker.publish(Text);
            list.add(new ChatItem(ChatType.OUT, BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher), Text));
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    chatAdapter.notifyDataSetChanged();
                    listView.invalidateViews();
                    listView.setSelection(list.size() - 1);
                }
            });
            if (isLast) {
                // TODO 最后的结果
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            mToast.setText("当前正在说话，音量大小：" + volume);
            mToast.show();
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }
    };


    /**
     * 合成监听器。
     */
    private SynthesizerListener mTtsListener = new SynthesizerListener() {

        @Override
        public void onSpeakBegin() {
            mToast.setText("语音合成开始");
            mToast.show();
        }

        @Override
        public void onSpeakPaused() {
        }

        @Override
        public void onSpeakResumed() {
        }

        @Override
        public void onBufferProgress(int percent, int beginPos, int endPos,
                                     String info) {
        }

        @Override
        public void onSpeakProgress(int percent, int beginPos, int endPos) {
        }

        @Override
        public void onCompleted(SpeechError error) {
            if (error == null) {
                mToast.setText("语音合成结束");
                mToast.show();
            } else if (error != null) {
                mToast.setText(error.getPlainDescription(true));
                mToast.show();
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }
    };

    public void asr_setParam(){

        mLocalGrammar = FucUtil.readFile(this,"command.bnf", "utf-8");
        mContent = new String(mLocalGrammar);
        ret = mAsr.buildGrammar("bnf", mContent, mLocalGrammarListener);
        if (ret != ErrorCode.SUCCESS) {
            mToast.setText("语法构建失败,错误码：" + ret);
            mToast.show();
        }

        mAsr.setParameter(SpeechConstant.PARAMS, null);
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
        mAsr.setParameter(SpeechConstant.RESULT_TYPE, "json");
        mAsr.setParameter(SpeechConstant.LOCAL_GRAMMAR, "command");
        mAsr.setParameter(SpeechConstant.ASR_THRESHOLD, "30");
        mAsr.setParameter(SpeechConstant.AUDIO_FORMAT,"wav");
        mAsr.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/讯飞语音平台/asr.wav");
    }

    public void iat_setParam() {
        mIat.setParameter(SpeechConstant.PARAMS, null);
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
        mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");
        mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin ");
        mIat.setParameter(SpeechConstant.VAD_BOS,"4000");
        mIat.setParameter(SpeechConstant.VAD_EOS,"1000");
        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIat.setParameter(SpeechConstant.ASR_PTT,"1");
        mIat.setParameter(SpeechConstant.AUDIO_FORMAT,"wav");
        mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/讯飞语音平台/iat.wav");
    }

    private void tts_setParam(){
        mTts.setParameter(PARAMS, null);
        mTts.setParameter(ENGINE_TYPE, TYPE_LOCAL);
        mTts.setParameter(VOICE_NAME, "xiaoyan");
        mTts.setParameter(SPEED, "50");
        mTts.setParameter(PITCH, "50");
        mTts.setParameter(VOLUME, "50");
        //设置播放器音频流类型
        mTts.setParameter(STREAM_TYPE, "3");
        // 设置播放合成音频打断音乐播放，默认为true
        mTts.setParameter(KEY_REQUEST_FOCUS, "true");
        mTts.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        mTts.setParameter(SpeechConstant.TTS_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/讯飞语音平台/tts.wav");
    }

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(getRosHostname());
        nodeConfiguration.setMasterUri(getMasterUri());
        nodeMainExecutor.execute(talker, nodeConfiguration);
        nodeMainExecutor.execute(listener, nodeConfiguration);
    }
}

