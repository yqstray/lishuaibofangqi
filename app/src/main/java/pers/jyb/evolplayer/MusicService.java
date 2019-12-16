package pers.jyb.evolplayer;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import static pers.jyb.evolplayer.MainActivity.musicList;


public class MusicService extends Service {
    private final int LIST_LOOP=0;
    private final int SINGLE_LOOP=1;
    private final int RANDOM_PLAY=2;

    private OpenPlayerReceiver openPlayerReceiver;
    private Communication communication;

    int mode;
    int position=-1;
    int positionList=-1;
    ListOfLists listOfLists;
    MusicList listHistory;
    Music music;
    List<Music> list;
    int musicNumber;
    MediaPlayer mediaPlayer = new MediaPlayer();

    private final IBinder binder = new MusicBinder();

    @Override
    public void onCreate() {
        super.onCreate();

        communication=Communication.get();
        listOfLists=ListOfLists.get(getApplicationContext());
        listHistory=listOfLists.getList().get(1);

        mode=LIST_LOOP;

        openPlayerReceiver=new OpenPlayerReceiver();
        IntentFilter openPLayerFilter=new IntentFilter();
        openPLayerFilter.addAction("OPEN_PLAYER");
        registerReceiver(openPlayerReceiver,openPLayerFilter);
    }



    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(openPlayerReceiver);
        stopMediaPlayer();
        communication.setServiceStarted(false);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        positionList=intent.getIntExtra("POSITION_LIST",-1);
        position=intent.getIntExtra("POSITION",-1);
        setMusic();
        if(music!=null){
            setMediaPlayer();
            mediaPlayer.start();
            updateHistory();
        }

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if(mediaPlayer!=null) {
                    Intent currentIntent = new Intent();
                    currentIntent.setAction(Intent.ACTION_VIEW);
                    currentIntent.putExtra("CURRENT_DURATION", mediaPlayer.getCurrentPosition());
                    currentIntent.putExtra("TOTAL_DURATION", mediaPlayer.getDuration());
                    sendBroadcast(currentIntent);
                }
            }
        }, 0, 1000);

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }


    public void setMusic() {
        list=musicList.getList();
        musicNumber=list.size();
        if(position!=-1) {
            music = list.get(position);
        }
    }

    public void stopMediaPlayer(){
        if(mediaPlayer!=null){
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer=null;
        }
    }


    public void setMediaPlayer(){
        if(music!=null) {
            Uri uri = Uri.parse(music.getData());
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            try {
                mediaPlayer.setDataSource(getApplicationContext(), uri);
                mediaPlayer.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if (mode == SINGLE_LOOP) {
                        mediaPlayer.seekTo(0);
                        mediaPlayer.start();
                    } else {
                        playNext();
                    }
                }
            });
        }
    }

    private void sendCompleteBroadcast(){
        Intent completeIntent=new Intent();
        completeIntent.putExtra("POSITION",position);
        completeIntent.setAction("COMPLETE");
        sendBroadcast(completeIntent);
    }
//随机播放
    public int randomChangeMusic(int now){
        Random random=new Random();
        int result=now;
        if(musicNumber!=1) {
            while (result == now) {
                result = random.nextInt(list.size());
            }
        }
        return result;
    }

    public void chang0eMode(int mode){
        this.mode=mode;
    }

    public void updateHistory(){
        if(listHistory.contains(music)) {
            listHistory.remove(music);
        }
        listHistory.addFirst(music);
    }

    private static final String TAG = "MusicService";
    public void playNext(){
        if (mode == RANDOM_PLAY) {
            position = randomChangeMusic(position);
        } else {
            position = (position + 1) % list.size();
        }
        list=listOfLists.getList().get(positionList).getList();
        Log.d(TAG, "playNext: positionList"+positionList+" position: "+position);
        music=list.get(position);
        mediaPlayer.release();
        mediaPlayer=new MediaPlayer();
        setMediaPlayer();
        mediaPlayer.start();
        updateHistory();
        sendCompleteBroadcast();
    }

    public void playPrev(){
        if (mode == RANDOM_PLAY) {
            position = randomChangeMusic(position);
        } else {
            position = position - 1;
            if (position < 0) {
                position = list.size() - 1;
            }
        }
        list=listOfLists.getList().get(positionList).getList();
        music=list.get(position);
        mediaPlayer.reset();
        setMediaPlayer();
        mediaPlayer.start();
        updateHistory();
        sendCompleteBroadcast();
    }

    public void changeMode(int single_loop) {
    }

    private class OpenPlayerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int positionOpen=intent.getIntExtra("POSITION_OPEN",0);
            int positionListOpen=intent.getIntExtra("POSITION_LIST_OPEN",0);
            if(positionListOpen!=positionList||positionOpen!=position){
                positionList=positionListOpen;
                position=positionOpen;
                setMusic();
                if (music != null) {
                    mediaPlayer.reset();
                    setMediaPlayer();
                    mediaPlayer.start();
                    updateHistory();
                }
            }
            Intent playerIntent=new Intent();
            playerIntent.putExtra("MODE",mode);
            playerIntent.setAction("MODE");
            sendBroadcast(playerIntent);
        }
    }

    class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }
}
