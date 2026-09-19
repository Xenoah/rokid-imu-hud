package dev.xenoah.hud;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import dev.xenoah.hud.core.Exchange;

/** No Internet/Bluetooth/location/camera/microphone permissions or companion services. */
public final class MainActivity extends Activity {
    private static final String RESET="com.android.action.ACTION_TWO_FINGER_DOUBLE_TAP";
    private static final String TAKE="com.rokid.sprite.ACTION_TAKE_STATUS_CHANGED";
    private static final String LEG="com.rokid.sprite.ACTION_LEG_STATUS_CHANGED";
    private SensorController sensors;
    private HudSurface surface;
    private Diagnostics diagnostics;
    private boolean registered,foreground,worn=true,open=true;
    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(!foreground)return;
            String action=intent.getAction();
            Log.i(Diagnostics.TAG,"input="+action);
            if(RESET.equals(action)){if(worn&&open)sensors.recenter();}
            else if(TAKE.equals(action)){
                String value=intent.getStringExtra("glasses_take_state");
                if("0".equals(value)||"1".equals(value)){worn="1".equals(value);updateRunning();}
            }else if(LEG.equals(action)){
                String value=intent.getStringExtra("glasses_leg_state");
                if("0".equals(value)||"1".equals(value)){open="1".equals(value);updateRunning();}
            }
            // Do not abort ordered broadcasts or suppress power, AI, pairing, or settings.
        }
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Log.i(Diagnostics.TAG,"startup: onCreate");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setDecorFitsSystemWindows(false);
        boolean debug=getIntent().getBooleanExtra("debug",false);
        Exchange exchange=new Exchange();
        sensors=new SensorController(this,exchange,getIntent().getBooleanExtra("force_fusion",false));
        surface=new HudSurface(this,exchange,debug);setContentView(surface);
        diagnostics=new Diagnostics(this,exchange,debug);
        // PhoneWindow.getInsetsController() dereferences mDecor on Android 12.
        // Install content first; use the View API, which can safely return null
        // before attachment. Retry when the window receives focus.
        hideSystemBars();
        Log.i(Diagnostics.TAG,"startup: content ready");
    }
    private void hideSystemBars(){
        WindowInsetsController bars=getWindow().getDecorView().getWindowInsetsController();
        if(bars!=null){
            bars.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            bars.hide(WindowInsets.Type.systemBars());
        }
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus)hideSystemBars();
    }
    @Override protected void onResume(){
        super.onResume();foreground=true;
        IntentFilter filter=new IntentFilter();filter.addAction(RESET);filter.addAction(TAKE);filter.addAction(LEG);
        // Standard platform API; no AndroidX dependency. Firmware broadcasts originate outside app UID.
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);
        else registerReceiver(receiver,filter);
        registered=true;updateRunning();
        Log.i(Diagnostics.TAG,"startup: resumed");
    }
    private void updateRunning(){
        if(foreground&&worn&&open){getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);sensors.start();surface.resume();}
        else{getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);sensors.stop();surface.pause();}
    }
    @Override protected void onPause(){
        foreground=false;updateRunning();
        if(registered){unregisterReceiver(receiver);registered=false;}
        super.onPause();
    }
    @Override protected void onDestroy(){sensors.close();surface.close();diagnostics.close();super.onDestroy();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getKeyCode()==KeyEvent.KEYCODE_ENTER){
            if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()&&event.getRepeatCount()==0)sensors.nextMode();
            return true;
        }
        // KEYCODE_BACK (single-finger double-tap) keeps normal exit/back behavior.
        return super.dispatchKeyEvent(event);
    }
}
