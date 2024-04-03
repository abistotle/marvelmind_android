package com.example.marvelmind_track;

import android.app.Activity;
import android.graphics.RectF;
import android.support.v4.app.ActivityCompat;
import android.content.pm.PackageManager;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.widget.Button;
import android.widget.LinearLayout;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.Manifest;
import android.view.Gravity;
import android.widget.TextView;

import java.sql.Timestamp;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.io.File;

import static java.lang.Math.round;

class TrackingDot {
    Vector3 pos;
    int color;
    long timestamp;

    TrackingDot(Vector3 pos_, int color_) {
        pos= pos_;
        color= color_;
        timestamp= System.currentTimeMillis();
    }
}

class TrackingDots extends LinkedList<TrackingDot>{
    public static final long DOTS_TIMEOUT_SEC = 100;

    Iterator<TrackingDot> dotsIterator;

    boolean dotIsTooOld(TrackingDot dot) {
        long t1= dot.timestamp;
        long t2= System.currentTimeMillis();

        return ((t2-t1)>DOTS_TIMEOUT_SEC*1000);
    }

    void addDot(Vector3 pos_, int color_) {
        Vector3 dotPos= new Vector3(pos_);
        add(new TrackingDot(dotPos, color_));
    }

    public TrackingDots() {
        super();
    }
}

class Point2D {
    double x;
    double y;

    public void set(double x_, double y_) {
        x= x_;
        y= y_;
    }

    public void rotate(float angle) {
        double angle_r= (double) angle*(3.14159f/180.0f);
        double cang= Math.cos(angle_r);
        double sang= Math.sin(angle_r);

        double xn= x*cang + y*(-sang);
        double yn= x*sang + y*cang;

        x= xn;
        y= yn;
      }

    public Point2D(double x_, double y_) {
        x= x_;
        y= y_;
    }
}

class MapView extends View {
    Paint p;

    Vector3 hedge_pos;
    boolean hedge_oriented;
    float hedge_angle;

    Point2D hedgeView[];

    TrackingDots dots;

    boolean gotoCenter= true;
    int shiftX= 0;
    int shiftY= 0;

    public MapView(Context context) {
        super(context);

        p = new Paint();

        hedgeView= new Point2D[4];
        for(int i=0;i<4;i++) {
            hedgeView[i]= new Point2D(0, 0);
        }

        hedge_pos= new Vector3(1.0f, 1.0f, 0.0f);
        hedge_oriented= true;
        hedge_angle= 0.0f;
        dots= new TrackingDots();

        File imgFile = new  File("/storage/emulated/0/DCIM/floor.png");
        //Bitmap bitmap = BitmapFactory.decodeFile(Environment.getRootDirectory()+"/floor.png");
        Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
        BitmapDrawable bitmapDrawable = new BitmapDrawable(context.getResources(), bitmap);
        bitmapDrawable.setGravity(Gravity.CENTER);
        this.setBackground(bitmapDrawable);
    }

    public Vector3 pos2Screen(Vector3 pos, RectF bounds) {
        Vector3 res= new Vector3(0.0f,0.0f,0.0f);

        res.x= shiftX + pos.x *50.0f;// todo: scale
        res.y= shiftY + 500.0f - pos.y *50.0f;
        res.z= pos.z *50.0f;

        if ((res.x<bounds.left)||(res.x>bounds.right)||(res.y<bounds.top)||(res.y>bounds.bottom)) {
            gotoCenter= true;
        }

        if (gotoCenter) {
            shiftX= shiftX + round((bounds.right - bounds.left) / 2) - round(res.x);
            shiftY= shiftY + round((bounds.bottom - bounds.top) / 2) - round(res.y);

            gotoCenter= false;
        }

        return res;
    }

    public void addPos(MarvelmindPos mpos) {
        if (mpos.pos.x>1000000.0f) return;
        if (mpos.pos.x<-1000000.0f) return;
        if (mpos.pos.y>1000000.0f) return;
        if (mpos.pos.y<-1000000.0f) return;

        hedge_pos.x= mpos.pos.x/1000.0f;
        hedge_pos.y= mpos.pos.y/1000.0f;
        hedge_pos.z= mpos.pos.z/1000.0f;

        hedge_oriented= mpos.oriented;
        hedge_angle= 360.0f - mpos.angle;

        dots.addDot(hedge_pos, Color.BLUE);

        invalidate();
    }

    void drawOrientedHedge(Canvas canvas, int x, int y, int size) {
        //hedgeView[0].set(0,0);
        //hedgeView[1].set(-size,-size);
        //hedgeView[2].set(size,0);
        //hedgeView[3].set(-size,size);

        hedgeView[0].set(-size,size);
        hedgeView[1].set(size,size / 2);
        hedgeView[2].set(size,-size/2);
        hedgeView[3].set(-size,-size);

        for(int i=0;i<4;i++) {
            hedgeView[i].rotate(hedge_angle);
        }

        Path wallpath = new Path();
        wallpath.reset(); // only needed when reusing this path for a new build
        wallpath.moveTo((float) (hedgeView[0].x + x), (float)(hedgeView[0].y + y)); // used for first point
        wallpath.lineTo((float) (hedgeView[1].x + x), (float)(hedgeView[1].y + y));
        wallpath.lineTo((float) (hedgeView[2].x + x), (float)(hedgeView[2].y + y));
        wallpath.lineTo((float) (hedgeView[3].x + x), (float)(hedgeView[3].y + y));
        wallpath.lineTo((float) (hedgeView[0].x + x), (float)(hedgeView[0].y + y));

        canvas.drawPath(wallpath, p);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        p.setColor(Color.BLUE);
        p.setStrokeWidth(10);
        p.setTextSize(60);
        p.setStyle(Style.FILL);

        RectF bounds = new RectF(canvas.getClipBounds());

        int n= 0;
        if (dots.size()>0) {
            TrackingDot dot = dots.get(n);
            while (dot != null) {
                if (dots.dotIsTooOld(dot)) {
                    dots.remove(dot);
                    if (dots.size()<=n) {
                        break;
                    }
                    dot = dots.get(n);
                    continue;
                }
                Vector3 ps = pos2Screen(dot.pos, bounds);

                p.setColor(dot.color);
                canvas.drawCircle((int) ps.x, (int) ps.y, 4, p);

                n++;
                if (dots.size()<=n) {
                    break;
                }
                dot = dots.get(n);
            }
        }

        p.setColor(Color.BLUE);

        Vector3 ps= pos2Screen(hedge_pos, bounds);
        if (!hedge_oriented) {
            canvas.drawCircle((int) ps.x, (int) ps.y, 25, p);
        } else {
            drawOrientedHedge(canvas, (int) ps.x, (int) ps.y, 25);
            //canvas.drawText(String.valueOf(hedge_angle), ps.x, ps.y, p);
        }

        //canvas.drawText(String.valueOf(hedge_pos.x), 20, 200, p);
        //canvas.drawText(String.valueOf(hedge_pos.y), 20, 300, p);
    }
}

public class MainActivity extends AppCompatActivity {
    public static final int MM_MSG_USB_CONNECTED = 101;
    public static final int MM_MSG_USB_DISCONNECTED = 102;
    public static final int MM_MSG_BT_CONNECTED = 105;
    public static final int MM_MSG_BT_SEARCHING = 106;
    public static final int MM_MSG_BT_DISCONNECTED = 107;
    public static final int MM_MSG_STREAM_DATA = 110;

    public static final int MM_BT_NOT_CONNECTED= 0;
    public static final int MM_BT_SEARCHING= 1;
    public static final int MM_BT_CONNECTED= 2;

    MapView mapView;
    MarvelmindUsb marvelmindUsb;

    Marvelmind mmStream;

    boolean usbConnected;
    int bluetoothStatus;

    int testV;
    int testFail;
    int testCnt;

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }


    public void onActivityResult(int requestCode, int resultCode, Intent data) {

    }

    public void setTitleE(String s) {
        TextView tv= findViewById(R.id.caption_text);
        if (tv == null) return;
        tv.setText(s);
    }

    public void setStatusE(String s) {
        TextView tv= findViewById(R.id.status_text);
        if (tv == null) return;
        tv.setText(s);
    }

    public void updTitle() {
        if (bluetoothStatus == MM_BT_SEARCHING) {
            setTitleE("Marvelmind : bluetooth scanning...");
            return;
        }

        if ((usbConnected) || (bluetoothStatus == MM_BT_CONNECTED)) {
            setTitleE("Marvelmind : connected");
            return;
        }

        setTitleE("Marvelmind : not connected");
    }

    public Handler msgHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MM_MSG_USB_CONNECTED: {
                    usbConnected= true;
                    updTitle();
                    break;
                }
                case MM_MSG_USB_DISCONNECTED: {
                    usbConnected= false;
                    updTitle();
                    break;
                }

                case MM_MSG_BT_CONNECTED: {
                    bluetoothStatus= MM_BT_CONNECTED;
                    updTitle();
                    break;
                }

                case MM_MSG_BT_SEARCHING: {
                    bluetoothStatus= MM_BT_SEARCHING;
                    updTitle();
                    break;
                }

                case MM_MSG_BT_DISCONNECTED: {
                    bluetoothStatus= MM_BT_NOT_CONNECTED;
                    updTitle();
                    break;
                }

                case MM_MSG_STREAM_DATA: {
                    Bundle bundle = msg.getData();
                    byte[] data = bundle.getByteArray("stream");
                    setStatusE(String.valueOf(testCnt));
                    testCnt++;
                    if (data != null) {
                        int frameId= -1;

                        boolean addOk= mmStream.addReceivedData(data);

                        //setStatusE(String.valueOf(data[0])+" "+String.valueOf(data[1]));

                        if (addOk) {
                            while ( (frameId= mmStream.findFrame()) > 0) {
                                switch (frameId) {
                                    case Marvelmind.MM_FRAME_HEDGE_POS_32BIT: {
                                        setStatusE("X= "+String.format("%.3f", mmStream.last_linear_values.pos.x/1000.0) +"   "+
                                                   "Y= "+String.format("%.3f", mmStream.last_linear_values.pos.y/1000.0)+"   "+
                                                   "Z= "+String.format("%.3f", mmStream.last_linear_values.pos.z/1000.0)+"   ");
                                        //setTitle(String.valueOf(mmStream.last_linear_values.pos.x));
                                        mapView.addPos(mmStream.last_linear_values);
                                        break;
                                    }

                                    case Marvelmind.MM_FRAME_HEDGE_POS_32BIT_NEWTIMESTAMPS: {
                                        setStatusE("X= "+String.format("%.3f", mmStream.last_linear_values.pos.x/1000.0)+"   "+
                                                "Y= "+String.format("%.3f", mmStream.last_linear_values.pos.y/1000.0)+"   "+
                                                "Z= "+String.format("%.3f", mmStream.last_linear_values.pos.z/1000.0)+"   ");
                                        //setTitle(String.valueOf(mmStream.last_linear_values.pos.x));
                                        mapView.addPos(mmStream.last_linear_values);
                                        break;
                                    }

                                    case Marvelmind.MM_FRAME_IMU_FUSION: {
                                        //setTitle(String.valueOf(mmStream.last_linear_values.angle));
                                        mapView.addPos(mmStream.last_linear_values);
                                        break;
                                    }

                                    case Marvelmind.MM_FRAME_HEDGE_POS_16BIT_SHORT: {
                                        testV++;
                                        //setTitle(String.valueOf(testV)+"  /  "+String.valueOf(testFail));
                                        mapView.addPos(mmStream.last_linear_values);
                                        break;
                                    }

                                    case Marvelmind.MM_FRAME_ANGLE_YAW_SHORT: {
                                        //setTitle(String.valueOf(mmStream.last_linear_values.angle));
                                        mapView.addPos(mmStream.last_linear_values);
                                        break;
                                    }
                                }//switch
                            }//while
                            //if (frameId<0) {
                            //    setStatusE("FrameId= "+String.valueOf(frameId));
                            //}
                            if (frameId == -2) {
                                testFail++;
                            }
                        }//if addOk
                    }

                    break;
                }
                default: {
                    setTitleE(String.valueOf(msg.what)+' '+String.valueOf(msg.arg1));
                    break;
                }
            }
            super.handleMessage(msg);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        usbConnected= false;
        bluetoothStatus= 0;
        testV= 0;
        testFail= 0;
        testCnt= 0;

        mapView=new MapView(this);

        mmStream= new Marvelmind();

        //setContentView(mapView);
        setContentView(R.layout.activity_main);

        ((LinearLayout) (ViewGroup) ((ViewGroup) this
                .findViewById(android.R.id.content)).getChildAt(0)).addView(mapView);

        setTitleE("Marvelmind: not connected");

        marvelmindUsb= new MarvelmindUsb(getSystemService(Context.USB_SERVICE), msgHandler, this);

        verifyStoragePermissions(this);
    }

    // 'clear map' button event
    public void clearMap(View view) {
        //setTitle("Map cleared");
        mapView.dots.clear();
    }
}
