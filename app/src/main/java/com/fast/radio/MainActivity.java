package com.fast.radio;

import android.os.Bundle;
import android.os.Environment;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private EditText urlInput, nameInput;
    private TextView info;
    private Button playButton, stopButton, recordButton, addButton;
    private ListenableFuture<MediaController> future;
    private MediaController controller;
    private RadioStation current;
    private volatile boolean recording;
    private Thread recordingThread;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        urlInput=findViewById(R.id.urlInput); nameInput=findViewById(R.id.nameInput);
        info=findViewById(R.id.info); playButton=findViewById(R.id.playButton);
        stopButton=findViewById(R.id.stopButton); recordButton=findViewById(R.id.recordButton);
        addButton=findViewById(R.id.addButton);

        future=new MediaController.Builder(this,
                new SessionToken(this, RadioPlaybackService.class)).buildAsync();
        future.addListener(() -> {
            try {
                controller=future.get();
                controller.addListener(new Player.Listener() {
                    @Override public void onPlaybackStateChanged(int s){ updateInfo(); }
                    @Override public void onIsPlayingChanged(boolean p){ updateInfo(); }
                    @Override public void onPlayerError(androidx.media3.common.PlaybackException e){
                        info.setText("خطا در پخش: "+e.getErrorCodeName());
                    }
                });
                updateInfo();
            } catch(Exception e){ info.setText("خطا در اتصال: "+e.getMessage()); }
        }, getMainExecutor());

        playButton.setOnClickListener(v->play());
        stopButton.setOnClickListener(v->{if(controller!=null)controller.stop();updateInfo();});
        addButton.setOnClickListener(v->saveStation());
        recordButton.setOnClickListener(v->{if(recording)stopRecording();else startRecording();});
    }

    private void play() {
        String url=urlInput.getText().toString().trim();
        String name=nameInput.getText().toString().trim();
        if(!(url.startsWith("http://")||url.startsWith("https://"))){
            toast("آدرس باید با HTTP یا HTTPS شروع شود."); return;
        }
        if(name.isEmpty()) name="Radio";
        current=new RadioStation(name,url,"");
        if(controller!=null){
            MediaItem item=new MediaItem.Builder().setUri(url).setMediaId(url)
                    .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(name).build()).build();
            controller.setMediaItem(item); controller.prepare(); controller.play();
            info.setText("Engine: Automatic → Media3 ExoPlayer\nProtocol: "+
                    (url.startsWith("https://")?"HTTPS":"HTTP")+"\nStatus: preparing");
        }
    }

    private void updateInfo() {
        if(controller==null)return;
        String s;
        switch(controller.getPlaybackState()){
            case Player.STATE_BUFFERING:s="Buffering";break;
            case Player.STATE_READY:s=controller.isPlaying()?"در حال پخش":"آماده";break;
            case Player.STATE_ENDED:s="پایان";break;
            default:s="Idle";
        }
        info.setText("Engine: Automatic → Media3 ExoPlayer\nStation: "+
                (current==null?"-":current.name)+"\nStatus: "+s);
    }

    private void saveStation(){
        String n=nameInput.getText().toString().trim(), u=urlInput.getText().toString().trim();
        if(n.isEmpty()||u.isEmpty()){toast("نام و آدرس را وارد کنید.");return;}
        getPreferences(MODE_PRIVATE).edit().putString("station_name",n)
                .putString("station_url",u).apply();
        toast("ایستگاه ذخیره شد.");
    }

    private void startRecording(){
        if(current==null){toast("ابتدا رادیو را پخش کنید.");return;}
        recording=true; recordButton.setText("توقف ضبط");
        File dir=new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC),"FastRadio");
        if(!dir.exists())dir.mkdirs();
        String t=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
        File out=new File(dir,current.name.replaceAll("[^a-zA-Z0-9آ-ی _-]","_")+"_"+t+".mp3");
        recordingThread=new Thread(()->recordStream(current.url,out)); recordingThread.start();
        info.setText("ضبط شروع شد\n"+out.getAbsolutePath());
    }

    private void stopRecording(){recording=false;recordButton.setText("ضبط");info.setText("ضبط متوقف شد.");}

    private void recordStream(String stream, File out){
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(stream).openConnection();
            c.setConnectTimeout(15000); c.setReadTimeout(15000); c.setRequestProperty("Icy-MetaData","0"); c.connect();
            if(c.getResponseCode()<200||c.getResponseCode()>=400)throw new IOException("HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream();FileOutputStream f=new FileOutputStream(out)){
                byte[] buf=new byte[8192]; int n;
                while(recording&&(n=in.read(buf))!=-1)f.write(buf,0,n);
            }
        }catch(Exception e){runOnUiThread(()->{recording=false;recordButton.setText("ضبط");info.setText("خطا در ضبط: "+e.getMessage());});}
        finally{if(c!=null)c.disconnect();}
    }

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){recording=false;if(future!=null)MediaController.releaseFuture(future);super.onDestroy();}
}
