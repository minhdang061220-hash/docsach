package vn.docsach;
import android.app.*;import android.content.Intent;import android.content.pm.ServiceInfo;import android.os.*;
public class KeepAlive extends Service{
 PowerManager.WakeLock wl;
 @Override public int onStartCommand(Intent i,int f,int id){
  String t=i!=null&&i.getStringExtra("t")!=null?i.getStringExtra("t"):"Đang đọc";
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel("r","Đọc truyện",NotificationManager.IMPORTANCE_LOW));
  PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"r"):new Notification.Builder(this);
  Notification n=b.setContentTitle("Đọc Sách").setContentText(t).setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(pi).setOngoing(true).build();
  if(Build.VERSION.SDK_INT>=29)startForeground(1,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);else startForeground(1,n);
  if(wl==null){wl=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"docsach:tts");wl.acquire(8*60*60*1000L);}
  return START_NOT_STICKY;
 }
 @Override public void onDestroy(){if(wl!=null&&wl.isHeld())wl.release();super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
