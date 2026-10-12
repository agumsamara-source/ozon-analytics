package ru.techenergostroy.stepone;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.hardware.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;

public class MainActivity extends Activity {
 static final String UPDATED="ru.techenergostroy.stepone.UPDATE", CH="walk";
 static final int NOTE=1234; Panel panel; boolean listening=false;
 BroadcastReceiver rx=new BroadcastReceiver(){public void onReceive(Context c,Intent i){ if(panel!=null)panel.invalidate(); }};
 static android.content.SharedPreferences pref(Context c){return c.getSharedPreferences("walk",0);}
 static String date(){return LocalDate.now().toString();}
 static long steps(Context c){return pref(c).getLong("n"+date(),0);}
 static float stride(Context c){return pref(c).getFloat("stride",74f);}
 static double kms(Context c){return pref(c).getFloat("meters"+date(),0f)/1000.0;}
 static int goal(Context c){return pref(c).getInt("goal",8000);}
 static String spaced(long n){return String.format(new Locale("ru"),"%,d",n).replace(',',' ');}
 static String km(double d){return String.format(new Locale("ru"),"%.2f",d);}
 static long record(Context c,long raw){
  android.content.SharedPreferences p=pref(c); long previous=p.getLong("raw",-1);
  long boot=p.getLong("boot",-1); long current=System.currentTimeMillis()-SystemClock.elapsedRealtime();
  boolean reset=previous<0||raw<previous||boot<0||Math.abs(boot-current)>120000;
  long delta=reset?0:Math.max(0,raw-previous);
  String k=date(); long count=p.getLong("n"+k,0)+delta;
  float meters=p.getFloat("meters"+k,0f)+delta*stride(c)/100f;
  p.edit().putLong("raw",raw).putLong("boot",current).putLong("n"+k,count).putFloat("meters"+k,meters).apply();
  return delta;
 }
 static void start(Context c){try{c.startForegroundService(new Intent(c,Tracker.class));}catch(Exception e){android.util.Log.e("StepOne","start",e);}}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.rgb(6,9,14));getWindow().setNavigationBarColor(Color.rgb(6,9,14));panel=new Panel(this,false);setContentView(panel);}
 @Override public void onResume(){super.onResume();if(!listening){IntentFilter f=new IntentFilter(UPDATED);if(Build.VERSION.SDK_INT>=33)registerReceiver(rx,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(rx,f);listening=true;}panel.invalidate(); if(pref(this).getBoolean("enabled",false))start(this);}
 @Override public void onPause(){if(listening){unregisterReceiver(rx);listening=false;}super.onPause();}
 void toggle(){
  if(pref(this).getBoolean("enabled",false)){pref(this).edit().putBoolean("enabled",false).apply();stopService(new Intent(this,Tracker.class));panel.invalidate();return;}
  SensorManager mgr=(SensorManager)getSystemService(SENSOR_SERVICE);
  if(mgr==null||mgr.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)==null){new AlertDialog.Builder(this).setTitle("Нет датчика шагов").setMessage("Энергосберегающий подсчёт на этом устройстве недоступен.").setPositiveButton("Понятно",null).show();return;}
  if(checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION},10);return;}activate();
 }
 void activate(){pref(this).edit().putBoolean("enabled",true).apply();start(this);panel.invalidate();if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);}
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==10&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)activate();}
 void settings(){
  LinearLayout v=new LinearLayout(this);v.setOrientation(1);int pad=(int)(20*getResources().getDisplayMetrics().density);v.setPadding(pad,5,pad,0);
  EditText h=new EditText(this),g=new EditText(this),len=new EditText(this);
  h.setInputType(2);g.setInputType(2);len.setInputType(8194);
  h.setSingleLine();g.setSingleLine();len.setSingleLine();
  h.setHint("Рост, см");h.setText(""+pref(this).getInt("height",175));
  g.setHint("Цель, шагов");g.setText(""+goal(this));
  len.setHint("Длина шага, см");len.setText(String.format(Locale.US,"%.1f",stride(this)));
  for(EditText e:new EditText[]{h,g,len})v.addView(e);
  new AlertDialog.Builder(this).setTitle("Параметры STEP•ONE").setView(v).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{try{
    int height=Integer.parseInt(h.getText().toString()),target=Integer.parseInt(g.getText().toString());float step=Float.parseFloat(len.getText().toString().replace(',','.'));
    if(height<90||height>230||target<100||target>100000||step<40||step>120)throw new Exception();
    pref(this).edit().putInt("height",height).putInt("goal",target).putFloat("stride",step).apply();panel.invalidate();
   }catch(Exception e){Toast.makeText(this,"Проверь рост, цель и длину шага",Toast.LENGTH_LONG).show();}}).show();
 }
 void calibrate(){
  android.content.SharedPreferences p=pref(this);
  if(!p.contains("calBase")){new AlertDialog.Builder(this).setTitle("Калибровка 100 метров").setMessage("Найди известные 100 м, нажми СТАРТ, пройди их и нажми 100 МЕТРОВ ещё раз. GPS не нужен.").setNegativeButton("Отмена",null).setPositiveButton("Старт",(d,w)->p.edit().putLong("calBase",steps(this)).putString("calDay",date()).apply()).show();}
  else {long n=steps(this)-p.getLong("calBase",0);new AlertDialog.Builder(this).setTitle("Шагов: "+n).setMessage("Ты прошёл ровно 100 метров? Тогда сохраним длину шага.").setNegativeButton("Отмена",null).setNeutralButton("Сбросить",(d,w)->p.edit().remove("calBase").remove("calDay").apply()).setPositiveButton("Готово",(d,w)->{float cm=n>0?10000f/n:0;if(cm<40||cm>120){Toast.makeText(this,"Необычное количество шагов. Повтори замер",Toast.LENGTH_LONG).show();return;}p.edit().putFloat("stride",cm).remove("calBase").remove("calDay").apply();Toast.makeText(this,"Длина шага: "+String.format(Locale.US,"%.1f см",cm),Toast.LENGTH_LONG).show();panel.invalidate();}).show();}
 }
 void ambient(){startActivity(new Intent(this,Ambient.class));}
 public static class Ambient extends Activity {
  @Override public void onCreate(Bundle b){super.onCreate(b);if(Build.VERSION.SDK_INT>=27)setShowWhenLocked(true);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().setNavigationBarColor(Color.BLACK);setContentView(new Panel(this,true));}
  @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()==1)finish();return true;}
 }
 public static class Dream extends android.service.dreams.DreamService {
  @Override public void onAttachedToWindow(){super.onAttachedToWindow();setInteractive(false);setFullscreen(true);setScreenBright(false);setContentView(new Panel(this,true));}
 }
 public static class Tracker extends Service implements SensorEventListener {
  SensorManager manager; boolean registered;long lastShown;long lastSteps;
  @Override public void onCreate(){super.onCreate();manager=(SensorManager)getSystemService(SENSOR_SERVICE);getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CH,"Подсчёт шагов",NotificationManager.IMPORTANCE_LOW));}
  Notification notification(){
   Intent main=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,main,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Intent ambient=new Intent(this,Ambient.class);PendingIntent ai=PendingIntent.getActivity(this,1,ambient,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   return new Notification.Builder(this,CH).setSmallIcon(android.R.drawable.ic_menu_directions).setContentTitle(spaced(steps(this))+" шагов • "+km(kms(this))+" км").setContentText("STEP•ONE • нажми, чтобы открыть").setContentIntent(pi).addAction(new Notification.Action.Builder((android.graphics.drawable.Icon)null,"Неоновый экран",ai).build()).setOngoing(true).setVisibility(Notification.VISIBILITY_PUBLIC).setOnlyAlertOnce(true).build();
  }
  @Override public int onStartCommand(Intent i,int f,int id){
   if(!pref(this).getBoolean("enabled",false)||checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED){stopSelf();return START_NOT_STICKY;}
   try{if(Build.VERSION.SDK_INT>=34)startForeground(NOTE,notification(),android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);else startForeground(NOTE,notification());}
   catch(Exception e){android.util.Log.e("StepOne","foreground",e);stopSelf();return START_NOT_STICKY;}
   if(!registered&&manager!=null){Sensor s=manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);registered=s!=null&&manager.registerListener(this,s,SensorManager.SENSOR_DELAY_NORMAL,10000000);}
   return START_STICKY;
  }
  @Override public void onSensorChanged(SensorEvent e){if(e.values.length==0)return;long n=record(this,(long)e.values[0]);if(n==0)return;sendBroadcast(new Intent(UPDATED).setPackage(getPackageName()));long now=SystemClock.elapsedRealtime();long total=steps(this);if(now-lastShown>60000||total-lastSteps>=100){getSystemService(NotificationManager.class).notify(NOTE,notification());lastSteps=total;lastShown=now;}}
  @Override public void onAccuracyChanged(Sensor s,int a){}
  @Override public void onDestroy(){if(manager!=null)manager.unregisterListener(this);super.onDestroy();}
  @Override public IBinder onBind(Intent i){return null;}
 }
 public static class Panel extends View {
  final Paint p=new Paint(3);final boolean ambient;final Context ctx;final int bg=0xff070a11,green=0xffa8ff3e,sub=0xff9aa6b1;float s,h; 
  Panel(Context c,boolean a){super(c);ambient=a;ctx=c;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
  void text(Canvas c,String t,float x,float y,float size,int color,boolean bold,Paint.Align align){
   p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(size);p.setTextAlign(align);p.clearShadowLayer();c.drawText(t,x,y,p);
  }
  void round(Canvas c,float l,float t,float r,float b,float rad,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);p.clearShadowLayer();c.drawRoundRect(l,t,r,b,rad,rad,p);}
  @Override public void onDraw(Canvas raw){
   raw.drawColor(bg);s=getWidth()/400f;h=getHeight()/s;raw.save();raw.scale(s,s);
   text(raw,"STEP",26,48,24,0xfff4f8f6,true,Paint.Align.LEFT);text(raw,"•ONE",91,48,24,green,true,Paint.Align.LEFT);
   text(raw,"УЛЬТРАЛЁГКИЙ ШАГОМЕР",26,73,10,sub,true,Paint.Align.LEFT);
   boolean running=pref(ctx).getBoolean("enabled",false);
   text(raw,running?"● АКТИВЕН":"○ ПАУЗА",374,49,11,running?green:sub,true,Paint.Align.RIGHT);
   float cy=ambient?Math.max(260,h*.49f):257;float radius=ambient?149:145;
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(14);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(0xff222a32);raw.drawArc(200-radius,cy-radius,200+radius,cy+radius,-90,360,false,p);
   p.setColor(green);p.setShadowLayer(14,0,0,0x60a8ff3e);raw.drawArc(200-radius,cy-radius,200+radius,cy+radius,-90,360*Math.min(1f,steps(ctx)/(float)Math.max(100,goal(ctx))),false,p);p.clearShadowLayer();p.setStyle(Paint.Style.FILL);
   text(raw,"ШАГИ СЕГОДНЯ",200,cy-48,13,sub,true,Paint.Align.CENTER);
   text(raw,spaced(steps(ctx)),200,cy+12,Math.min(64,steps(ctx)>99999?53:64),0xffffffff,true,Paint.Align.CENTER);
   text(raw,km(kms(ctx))+" км",200,cy+66,35,green,true,Paint.Align.CENTER);
   text(raw,"ЦЕЛЬ "+spaced(goal(ctx))+"   •   "+(Math.round(100f*steps(ctx)/Math.max(100,goal(ctx))))+"%",200,cy+102,13,sub,false,Paint.Align.CENTER);
   if(!ambient){
    float y=Math.max(430,h-275);
    round(raw,22,y,378,y+83,21,0xff161c25);
    text(raw,"ДЛИНА ШАГА",40,y+27,11,sub,true,Paint.Align.LEFT);
    text(raw,String.format(new Locale("ru"),"%.1f см",stride(ctx)),40,y+62,23,0xffffffff,true,Paint.Align.LEFT);
    text(raw,"ЗА 7 ДНЕЙ",223,y+27,11,sub,true,Paint.Align.LEFT);
    long week=0;for(int k=0;k<7;k++)week+=pref(ctx).getLong("n"+LocalDate.now().minusDays(k),0);
    text(raw,spaced(week),223,y+62,23,0xffffffff,true,Paint.Align.LEFT);
    round(raw,22,y+101,378,y+157,17,green);
    text(raw,running?"ПРИОСТАНОВИТЬ ПОДСЧЁТ":"ВКЛЮЧИТЬ ПОДСЧЁТ",200,y+136,15,bg,true,Paint.Align.CENTER);
    round(raw,22,y+171,195,y+224,15,0xff202937);round(raw,205,y+171,378,y+224,15,0xff202937);
    text(raw,"⚙  НАСТРОЙКИ",108,y+204,13,0xffffffff,true,Paint.Align.CENTER);
    text(raw,"100 МЕТРОВ",291,y+204,13,0xffffffff,true,Paint.Align.CENTER);
    text(raw,"ЗАСТАВКА  ↗",200,y+250,12,green,true,Paint.Align.CENTER);
   }else{text(raw,"КОСНИСЬ, ЧТОБЫ ВЫЙТИ",200,h-42,12,sub,false,Paint.Align.CENTER);}
   raw.restore();
  }
  @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_UP)return true;
   if(ambient){if(ctx instanceof Activity)((Activity)ctx).finish();return true;}
   if(!(ctx instanceof MainActivity))return true;MainActivity a=(MainActivity)ctx;float x=e.getX()/s,y=e.getY()/s,b=Math.max(430,h-275);
   if(y>=b+101&&y<=b+157)a.toggle();
   else if(y>=b+171&&y<=b+224){if(x<200)a.settings();else a.calibrate();}
   else if(y>b+226)a.ambient();
   return true;
  }
 }
}
