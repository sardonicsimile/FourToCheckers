package com.tablink.board;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoTrack;
import org.webrtc.DataChannel;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Native screen receiver for the Android 8 Boxlight.
 * Server controls the pairing codes; video uses WebRTC with a hardware decoder
 * and a low-FPS JPEG compatibility fallback if the WebRTC route fails.
 *
 * This is an experimental test build, not a verified production screen-casting app.
 */
public final class BoardActivity extends Activity {
  private static final String HOST = "https://tablink.onrender.com";
  private final ScheduledExecutorService bg = Executors.newScheduledThreadPool(3);
  private final AtomicInteger rxFrames = new AtomicInteger(0);
  private final List<IceCandidate> earlyIce = new ArrayList<>();

  private LinearLayout overlay;
  private TextView codeText, statusText, fpsText;
  private SurfaceViewRenderer renderer;
  private ImageView jpegView;
  private EglBase egl;
  private PeerConnectionFactory factory;
  private PeerConnection peer;
  private ScheduledFuture<?> pollTask, statsTask, jpegTask;
  private volatile String roomCode, viewerToken, frameAfter = "";
  private volatile boolean disposed = false, videoReceived = false;
  private int lastRx = 0;

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    getWindow().getDecorView().setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    buildUi();
    try {
      egl = EglBase.create();
      PeerConnectionFactory.initialize(
          PeerConnectionFactory.InitializationOptions.builder(getApplicationContext())
              .createInitializationOptions());
      factory = PeerConnectionFactory.builder()
          .setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext()))
          .createPeerConnectionFactory();
      renderer.init(egl.getEglBaseContext(), null);
      renderer.setEnableHardwareScaler(true);
      renderer.setMirror(false);
      setStatus("Ready to generate a connection code");
      newRoom();
    } catch (Exception ex) { setStatus("Video initialization failed: " + ex.getMessage()); }
  }

  private TextView label(String text, int size, int color, boolean bold) {
    TextView v = new TextView(this);
    v.setText(text); v.setTextSize(size); v.setTextColor(color);
    if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    v.setGravity(Gravity.CENTER);
    return v;
  }
  private int dp(int d) { return (int)(d * getResources().getDisplayMetrics().density); }

  private void buildUi() {
    FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Color.rgb(15,18,25));
    renderer = new SurfaceViewRenderer(this);
    root.addView(renderer,new FrameLayout.LayoutParams(-1,-1));
    jpegView = new ImageView(this);
    jpegView.setScaleType(ImageView.ScaleType.FIT_CENTER);
    jpegView.setVisibility(View.GONE);
    root.addView(jpegView,new FrameLayout.LayoutParams(-1,-1));
    overlay = new LinearLayout(this);overlay.setOrientation(LinearLayout.VERTICAL);
    overlay.setPadding(dp(36),dp(20),dp(36),dp(20));
    overlay.setGravity(Gravity.CENTER);
    overlay.setBackgroundColor(0xEE151923);
    root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));

    TextView brand = label("TabLink • Board Receiver",28,0xFFA4B4FF,true);
    overlay.addView(brand);
    TextView expl = label("Enter this code on the device that is sharing its screen",20,0xFFDDDFE8,false);
    LinearLayout.LayoutParams explain = new LinearLayout.LayoutParams(-1,-2);
    explain.setMargins(0,dp(18),0,dp(20));overlay.addView(expl,explain);
    codeText = label("——————",70,Color.WHITE,true);
    codeText.setLetterSpacing(0.09f);
    overlay.addView(codeText);
    statusText = label("Preparing connection…",17,0xFFBFC8DC,false);
    LinearLayout.LayoutParams sub = new LinearLayout.LayoutParams(-1,-2);
    sub.setMargins(0,dp(20),0,dp(14));overlay.addView(statusText,sub);
    Button newCode = new Button(this);newCode.setText("Generate fresh code");
    newCode.setOnClickListener(v -> newRoom());
    LinearLayout.LayoutParams buttonParams=new LinearLayout.LayoutParams(dp(300),dp(54));
    buttonParams.gravity=Gravity.CENTER;overlay.addView(newCode,buttonParams);
    TextView link = label("On your other device, open: tablink.onrender.com",17,0xFFBFC8DC,false);
    LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(-1,-2);
    linkParams.setMargins(0,dp(20),0,0);overlay.addView(link,linkParams);
    TextView note = label("Experimental • Works only if both networks allow the connection",13,0xFF8993A7,false);
    LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1,-2);
    noteParams.setMargins(0,dp(14),0,0);overlay.addView(note,noteParams);

    fpsText=label("",17,0xFFEEEFEE,true);
    FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(240),dp(45),
      Gravity.RIGHT | Gravity.BOTTOM);
    fp.setMargins(0,0,dp(12),dp(10));root.addView(fpsText,fp);
    renderer.setOnClickListener(v->overlay.setVisibility(overlay.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
    jpegView.setOnClickListener(v->overlay.setVisibility(overlay.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
    setContentView(root);
  }
  private void setStatus(String msg) {
    runOnUiThread(()->{if(!disposed)statusText.setText(msg);});
  }

  private synchronized void clearSession() {
    if(pollTask!=null){pollTask.cancel(false);pollTask=null;}
    if(jpegTask!=null){jpegTask.cancel(false);jpegTask=null;}
    if(statsTask!=null){statsTask.cancel(false);statsTask=null;}
    roomCode=null;viewerToken=null;videoReceived=false;frameAfter="";lastRx=0;
    earlyIce.clear();
    if(peer!=null){peer.close();peer.dispose();peer=null;}
    runOnUiThread(()->{
      if(disposed)return;
      jpegView.setVisibility(View.GONE);renderer.setVisibility(View.VISIBLE);
      overlay.setVisibility(View.VISIBLE);fpsText.setText("");
      codeText.setText("——————");
    });
  }

  private void newRoom() {
    clearSession();
    if(disposed)return;
    setStatus("Generating secure code…");
    bg.execute(()->{
      try {
        JSONObject answer=postJson("/api/create",new JSONObject(),null);
        String code=answer.getString("code");
        String token=answer.getString("token");
        JSONArray ice=answer.optJSONArray("ice");
        if(disposed)return;
        roomCode=code;viewerToken=token;
        buildPeer(ice);
        runOnUiThread(()->{if(!disposed)codeText.setText(code.replace("", " ").trim());});
        setStatus("Waiting for your iPhone or tablet…");
        pollTask=bg.scheduleWithFixedDelay(this::poll,0,650,TimeUnit.MILLISECONDS);
        statsTask=bg.scheduleWithFixedDelay(this::updateStats,1,1,TimeUnit.SECONDS);
        jpegTask=bg.scheduleWithFixedDelay(this::jpegFallback,2,1,TimeUnit.SECONDS);
      } catch(Exception ex){setStatus("Cannot reach TabLink: " + ex.getMessage());}
    });
  }

  private JSONObject requestJson(String method,String path,JSONObject payload,String token) throws Exception {
    HttpURLConnection c=(HttpURLConnection)new URL(HOST+path).openConnection();
    c.setConnectTimeout(6000);c.setReadTimeout(7000);c.setRequestMethod(method);
    c.setUseCaches(false);
    if(token!=null)c.setRequestProperty("Authorization","Bearer "+token);
    try {
      if(payload!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
        byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);
        try(OutputStream o=c.getOutputStream()){o.write(bytes);}
      }
      int status=c.getResponseCode();
      if(status<200||status>=300)throw new IllegalStateException("HTTP "+status);
      try(InputStream in=c.getInputStream()){
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
        while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>150000)throw new IllegalStateException("Response too big");}
        return new JSONObject(out.toString("UTF-8"));
      }
    }finally{c.disconnect();}
  }
  private JSONObject postJson(String path,JSONObject payload,String token) throws Exception {
    return requestJson("POST",path,payload,token);
  }

  private void buildPeer(JSONArray ice) {
    List<PeerConnection.IceServer> servers=new ArrayList<>();
    if(ice!=null)for(int i=0;i<ice.length();i++){
      JSONObject item=ice.optJSONObject(i);if(item==null)continue;
      Object urls=item.opt("urls");List<String> list=new ArrayList<>();
      if(urls instanceof String)list.add((String)urls);
      else if(urls instanceof JSONArray)for(int j=0;j<((JSONArray)urls).length();j++)
        list.add(((JSONArray)urls).optString(j));
      if(list.isEmpty())continue;
      PeerConnection.IceServer.Builder builder=PeerConnection.IceServer.builder(list);
      if(item.has("username"))builder.setUsername(item.optString("username"));
      if(item.has("credential"))builder.setPassword(item.optString("credential"));
      servers.add(builder.createIceServer());
    }
    PeerConnection.RTCConfiguration cfg=new PeerConnection.RTCConfiguration(servers);
    cfg.sdpSemantics=PeerConnection.SdpSemantics.UNIFIED_PLAN;
    peer=factory.createPeerConnection(cfg,new PeerConnection.Observer(){
      @Override public void onSignalingChange(PeerConnection.SignalingState s){}
      @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s){
        setStatus("Network connection: "+s.toString());
      }
      @Override public void onIceConnectionReceivingChange(boolean r){}
      @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s){}
      @Override public void onIceCandidate(IceCandidate c){
        try{JSONObject candidate=new JSONObject();
          candidate.put("candidate",c.sdp);candidate.put("sdpMid",c.sdpMid);
          candidate.put("sdpMLineIndex",c.sdpMLineIndex);
          JSONObject msg=new JSONObject();msg.put("type","ice");msg.put("candidate",candidate);
          sendSignal(msg);}catch(Exception ignored){}
      }
      @Override public void onIceCandidatesRemoved(IceCandidate[] c){}
      @Override public void onAddStream(MediaStream s){
        if(s!=null&&!s.videoTracks.isEmpty())attachVideo(s.videoTracks.get(0));
      }
      @Override public void onRemoveStream(MediaStream s){}
      @Override public void onDataChannel(DataChannel c){}
      @Override public void onRenegotiationNeeded(){}
      @Override public void onAddTrack(RtpReceiver receiver,MediaStream[] streams){
        if(receiver!=null&&receiver.track() instanceof VideoTrack)
          attachVideo((VideoTrack)receiver.track());
      }
      // Newer WebRTC SDKs also report Unified-Plan tracks through onTrack.
      public void onTrack(RtpTransceiver transceiver){
        if(transceiver!=null&&transceiver.getReceiver()!=null &&
            transceiver.getReceiver().track() instanceof VideoTrack)
          attachVideo((VideoTrack)transceiver.getReceiver().track());
      }
    });
    if(peer==null)throw new IllegalStateException("Cannot initialize video peer");
  }

  private void attachVideo(VideoTrack track) {
    if(track==null||videoReceived)return;
    videoReceived=true;
    runOnUiThread(()->{
      if(disposed)return;
      try{
        track.addSink(renderer);
        track.addSink(new VideoSink(){
          @Override public void onFrame(VideoFrame f){rxFrames.incrementAndGet();}
        });
        renderer.setVisibility(View.VISIBLE);
        jpegView.setVisibility(View.GONE);overlay.setVisibility(View.GONE);
        setStatus("Receiving live WebRTC video");
      }catch(Exception ex){setStatus("Video renderer error: "+ex.getMessage());}
    });
  }

  private void poll(){
    String code=roomCode,token=viewerToken;
    if(disposed||code==null||token==null)return;
    try {
      JSONObject response=requestJson("GET","/api/poll/"+code,null,token);
      if(response.optBoolean("ended",false)){setStatus("Sender stopped sharing. Generate a new code.");return;}
      JSONArray messages=response.optJSONArray("messages");
      if(messages==null)return;
      for(int i=0;i<messages.length();i++){
        JSONObject msg=messages.optJSONObject(i);if(msg==null)continue;
        String type=msg.optString("type","");
        if("joined".equals(type))setStatus("Device paired. Connecting video…");
        else if("offer".equals(type))onOffer(msg.optString("sdp"));
        else if("ice".equals(type))onRemoteIce(msg.optJSONObject("candidate"));
        else if("ended".equals(type))setStatus("Sender disconnected. Generate a new code.");
      }
    }catch(Exception e){setStatus("Connection retrying: "+e.getMessage());}
  }

  private void onOffer(String sdp){
    if(peer==null||sdp==null||sdp.length()<10)return;
    peer.setRemoteDescription(new SDP(){
      @Override public void onSetSuccess(){
        synchronized(earlyIce){for(IceCandidate c:earlyIce)peer.addIceCandidate(c);earlyIce.clear();}
        peer.createAnswer(new SDP(){
          @Override public void onCreateSuccess(SessionDescription answer){
            peer.setLocalDescription(new SDP(){
              @Override public void onSetSuccess(){
                try{
                  JSONObject out=new JSONObject();out.put("type","answer");out.put("sdp",answer.description);
                  sendSignal(out);
                }catch(Exception ignored){}
              }
            },answer);
          }
        },new MediaConstraints());
      }
    },new SessionDescription(SessionDescription.Type.OFFER,sdp));
  }

  private void onRemoteIce(JSONObject j){
    if(j==null||peer==null)return;
    IceCandidate c=new IceCandidate(j.optString("sdpMid"),
        j.optInt("sdpMLineIndex"),j.optString("candidate"));
    if(peer.getRemoteDescription()!=null)peer.addIceCandidate(c);
    else synchronized(earlyIce){earlyIce.add(c);}
  }
  private void sendSignal(JSONObject msg){
    String code=roomCode,token=viewerToken;
    if(disposed||code==null||token==null)return;
    bg.execute(()->{
      try{postJson("/api/signal/"+code,msg,token);}
      catch(Exception e){setStatus("Signaling: "+e.getMessage());}
    });
  }
  private void updateStats(){
    if(disposed||roomCode==null)return;
    int current=rxFrames.get(),received=Math.max(0,current-lastRx);lastRx=current;
    if(videoReceived)runOnUiThread(()->{
      if(!disposed)fpsText.setText("≈"+received+" received FPS");
    });
  }

  private void jpegFallback(){
    if(disposed||videoReceived||roomCode==null||viewerToken==null)return;
    HttpURLConnection c=null;
    try {
      c=(HttpURLConnection)new URL(HOST+"/api/frame/"+roomCode+"?after="+frameAfter).openConnection();
      c.setRequestProperty("Authorization","Bearer "+viewerToken);
      c.setConnectTimeout(5000);c.setReadTimeout(5000);c.setUseCaches(false);
      if(c.getResponseCode()!=200)return;
      String id=c.getHeaderField("X-Frame-Id");if(id!=null)frameAfter=id;
      Bitmap b;try(InputStream in=c.getInputStream()){b=BitmapFactory.decodeStream(in);}
      if(b!=null)runOnUiThread(()->{
        if(disposed||videoReceived)return;
        jpegView.setImageBitmap(b);
        renderer.setVisibility(View.GONE);
        jpegView.setVisibility(View.VISIBLE);
        overlay.setVisibility(View.GONE);
        fpsText.setText("HTTPS fallback • low FPS");
      });
    }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}
  }

  private static class SDP implements SdpObserver {
    @Override public void onCreateSuccess(SessionDescription s){}
    @Override public void onSetSuccess(){}
    @Override public void onCreateFailure(String e){}
    @Override public void onSetFailure(String e){}
  }

  @Override protected void onDestroy(){
    disposed=true;clearSession();bg.shutdownNow();
    if(renderer!=null)renderer.release();
    if(factory!=null)factory.dispose();
    if(egl!=null)egl.release();
    super.onDestroy();
  }
}
