package vn.docsach;
import android.app.Activity;import android.content.Intent;import android.graphics.Color;import android.net.Uri;import android.os.Build;import android.os.Bundle;
import android.speech.tts.TextToSpeech;import android.speech.tts.UtteranceProgressListener;import android.speech.tts.Voice;
import android.webkit.*;import org.json.*;import java.util.*;
public class MainActivity extends Activity{
 WebView w;ValueCallback<Uri[]> cb;TextToSpeech tts;
 volatile boolean ready=false,speaking=false,endQ=false;
 JSONArray q;volatile int gen=0,base=0,next=0,out=0,curIdx=0;volatile float rate=1f;
 final Object lock=new Object();volatile String status="loading";

 void js(final String s){runOnUiThread(()->{if(w!=null)w.evaluateJavascript(s,null);});}

 void initTts(){
  ready=false;status="loading";
  try{
   tts=new TextToSpeech(this,st->{
    if(st!=TextToSpeech.SUCCESS){status="noengine";return;}
    int r=tts.setLanguage(new Locale("vi","VN"));
    if(r==TextToSpeech.LANG_MISSING_DATA||r==TextToSpeech.LANG_NOT_SUPPORTED){status="novi";return;}
    tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
     @Override public void onStart(String id){
      String p="g"+gen+"_s";
      if(id.startsWith(p)){try{curIdx=Integer.parseInt(id.substring(p.length()));js("window.onNativeSentence&&onNativeSentence("+curIdx+")");}catch(Exception e){}}
     }
     @Override public void onDone(String id){done(id);}
     @Override public void onError(String id){done(id);}
    });
    ready=true;status="ok";
   },"com.google.android.tts");
  }catch(Exception e){status="noengine";}
 }

 void done(String id){
  String g="g"+gen+"_";
  if(!id.startsWith(g))return;
  if(id.equals(g+"end")){speaking=false;js("window.onNativeEnd&&onNativeEnd()");return;}
  if(id.startsWith(g+"s")){synchronized(lock){out--;}pump();}
 }

 void pump(){
  synchronized(lock){
   if(q==null)return;
   String g="g"+gen+"_";
   while(next<q.length()&&out<5){
    try{
     JSONObject o=q.getJSONObject(next);String t=o.optString("t","");int idx=base+next;
     if(t.trim().length()>0){
      tts.setPitch((float)o.optDouble("p",1));tts.setSpeechRate(rate*(float)o.optDouble("r",1));
      tts.speak(t,TextToSpeech.QUEUE_ADD,null,g+"s"+idx);
      tts.playSilentUtterance(o.optLong("g",300),TextToSpeech.QUEUE_ADD,g+"p"+idx);
      out++;
     }
    }catch(Exception e){}
    next++;
   }
   if(next>=q.length()&&!endQ){endQ=true;tts.playSilentUtterance(1,TextToSpeech.QUEUE_ADD,g+"end");}
  }
 }

 void applyVoice(String name){
  Voice pick=null;
  try{
   tts.setLanguage(new Locale("vi","VN"));
   Set<Voice> vs=tts.getVoices();
   if(vs!=null){
    for(Voice v:vs)if(v.getName().equals(name)&&"vi".equals(v.getLocale().getLanguage())){pick=v;break;}
    if(pick==null)for(Voice v:vs){
     if("vi".equals(v.getLocale().getLanguage())&&!v.getFeatures().contains("notInstalled")){
      if(pick==null||(pick.isNetworkConnectionRequired()&&!v.isNetworkConnectionRequired()))pick=v;
     }
    }
   }
  }catch(Exception e){}
  if(pick!=null)tts.setVoice(pick);
 }

 class Bridge{
  @JavascriptInterface public boolean ready(){return ready;}
  @JavascriptInterface public String status(){return status;}
  @JavascriptInterface public void speak(String json,int start,float r,String voice,String title){
   if(!ready)return;
   try{
    synchronized(lock){gen++;q=new JSONArray(json);base=start;next=0;out=0;endQ=false;rate=r;}
    tts.stop();applyVoice(voice);speaking=true;
    final String t=title;
    runOnUiThread(()->{Intent i=new Intent(MainActivity.this,KeepAlive.class).putExtra("t",t);
     try{if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}catch(Exception e){}});
    pump();
   }catch(Exception e){}
  }
  @JavascriptInterface public void setRate(float r){rate=r;}
  @JavascriptInterface public void stop(){
   synchronized(lock){gen++;q=null;}
   speaking=false;
   try{if(tts!=null)tts.stop();}catch(Exception e){}
   runOnUiThread(()->stopService(new Intent(MainActivity.this,KeepAlive.class)));
  }
  @JavascriptInterface public String voices(){
   JSONArray a=new JSONArray();
   try{
    if(ready){
     Set<Voice> vs=tts.getVoices();
     if(vs!=null){
      List<Voice> l=new ArrayList<>();
      for(Voice v:vs)if("vi".equals(v.getLocale().getLanguage())&&!v.getFeatures().contains("notInstalled"))l.add(v);
      Collections.sort(l,(x,y)->x.getName().compareTo(y.getName()));
      int k=1;
      for(Voice v:l){
       JSONObject o=new JSONObject();o.put("n",v.getName());o.put("l",v.getLocale().toLanguageTag());
       o.put("d","Google tiếng Việt "+(k++)+(v.isNetworkConnectionRequired()?" (cần mạng)":" (offline)"));
       a.put(o);
      }
     }
    }
   }catch(Exception e){}
   return a.toString();
  }
  @JavascriptInterface public void installVoice(){
   runOnUiThread(()->{
    if("noengine".equals(status)){
     try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id=com.google.android.tts")));}
     catch(Exception e){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.tts")));}catch(Exception e2){}}
     return;
    }
    try{startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage("com.google.android.tts"));}
    catch(Exception e){try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}catch(Exception e2){}}
   });
  }
 }

 @Override protected void onCreate(Bundle b){
  super.onCreate(b);
  getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
  if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},2);
  w=new WebView(this);w.setBackgroundColor(Color.BLACK);setContentView(w);
  WebSettings s=w.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(true);s.setMediaPlaybackRequiresUserGesture(false);
  w.addJavascriptInterface(new Bridge(),"Native");
  w.setWebViewClient(new WebViewClient());
  w.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> c,FileChooserParams p){
    if(cb!=null)cb.onReceiveValue(null);cb=c;
    Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
    try{startActivityForResult(Intent.createChooser(i,"Chọn file EPUB"),1);}catch(Exception e){cb=null;return false;}
    return true;}});
  w.loadUrl("file:///android_asset/index.html");
 }
 @Override protected void onResume(){
  super.onResume();
  if(tts==null)initTts();
  else if(!speaking){try{tts.shutdown();}catch(Exception e){}initTts();}
  else js("window.onNativeSentence&&onNativeSentence("+curIdx+")");
 }
 @Override protected void onDestroy(){
  try{if(tts!=null)tts.shutdown();}catch(Exception e){}
  stopService(new Intent(this,KeepAlive.class));
  super.onDestroy();
 }
 @Override protected void onActivityResult(int r,int c,Intent d){
  if(r==1&&cb!=null){Uri[] u=null;if(c==RESULT_OK&&d!=null&&d.getData()!=null)u=new Uri[]{d.getData()};cb.onReceiveValue(u);cb=null;}
 }
 @Override public void onBackPressed(){
  w.evaluateJavascript("window.goBack&&goBack()",v->{if(!"true".equals(v))finish();});
 }
}
