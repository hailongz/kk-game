package cn.kkmofang.game;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.renderscript.Script;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import cn.kkmofang.script.IScriptFunction;
import cn.kkmofang.script.ScriptContext;
import cn.kkmofang.view.Element;
import cn.kkmofang.view.ViewElement;
import cn.kkmofang.view.value.Color;
import cn.kkmofang.view.value.Pixel;
import cn.kkmofang.view.value.V;

import static android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY;

/**
 * Created by hailong11 on 2018/4/17.
 */

public class GLViewElement extends ViewElement {

    private Context _context;
    private String _basePath;
    private boolean _loaded;
    private int _width = 0;
    private int _height = 0;

    public final Handler handler;

    public GLViewElement() {
        super();
        handler = new Handler();
        _loaded = false;
    }

    public Class<?> viewClass() {
        return GLSurfaceView.class;
    }


    public void emit(MotionEvent event) {

        if(_context == null) {
            return ;
        }

        float width = this.width();
        float height = this.height();

        if(width <= 0.0f || height <= 0.0f) {
            return;
        }

        final Map<String,Object> data = new TreeMap<>();
        final List<Object> move = new ArrayList<>();
        final List<Object> act = new ArrayList<>();

        data.put("width",width);
        data.put("height",height);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                data.put("type","touchstart");
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                data.put("type","touchend");
                break;
            case MotionEvent.ACTION_MOVE:
                data.put("type","touchmove");
                break;
            default:
                data.put("type","touchcancel");
                break;
        }

        for (int i = 0; i < event.getPointerCount(); i++) {

            Map<String, Object> item = new TreeMap<>();

            float x = event.getX(i);
            float y = event.getY(i);

            item.put("dx", x * 2.0f / width - 1.0f);
            item.put("dy", y * 2.0f / height - 1.0f);
            item.put("x", x);
            item.put("y", y);
            item.put("id", String.valueOf(event.getPointerId(i) + 1));

            if(i == event.getActionIndex()
                    && event.getActionMasked() != MotionEvent.ACTION_MOVE) {
                act.add(item);
            } else {
                move.add(item);
            }

        }

        Log.d("kk","[GL] [TOUCH]");

        _context.post(new Runnable() {
            @Override
            public void run() {
                Context ctx = (Context) ScriptContext.currentContext();

                if(act.size() > 0) {
                    data.put("items",act);
                    ctx.emit(V.stringValue(data.get("type"),""),data);
                } else if(move.size() >0){
                    data.put("items",move);
                    data.put("type","touchmove");
                    ctx.emit("touchmove",data);
                }
            }
        });

    }


    public void setView(View view) {

        uninstallGLContext();

        super.setView(view);

        if(view != null && view instanceof GLSurfaceView) {

            _basePath = get("path");

            final WeakReference<GLViewElement> e = new WeakReference<>(this);

            GLSurfaceView glView = (GLSurfaceView) view;

            glView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {

                    GLViewElement element = e.get();

                    if(element != null) {

                        element.emit(motionEvent);


                        return true;
                    }

                    return false;
                }
            });

            glView.setEGLContextClientVersion(2);
            glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
            glView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
            glView.setRenderer(new GLSurfaceView.Renderer() {

                @Override
                public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                    GLViewElement v = e.get();
                    if(v != null) {
                        v.onSurfaceCreated(gl10,eglConfig);
                    }
                }

                @Override
                public void onSurfaceChanged(GL10 gl10, int width, int height) {
                    GLViewElement v = e.get();
                    if(v != null) {
                        v.onSurfaceChanged(gl10,width,height);
                    }
                }

                @Override
                public void onDrawFrame(GL10 gl10) {
                    GLViewElement v = e.get();
                    if(v != null) {
                        v.onDrawFrame(gl10);
                    }
                }

            });
            glView.setRenderMode(RENDERMODE_CONTINUOUSLY);

        }
    }

    private final static IScriptFunction AppOn = new IScriptFunction() {
        @Override
        public int call() {
            return 0;
        }
    };

    protected void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {

        GLES20.glClearColor(0,0,0,0);

        installGLContext();

    }

    protected void onSurfaceChanged(GL10 gl10, int width, int height) {
        _width = width;
        _height = height;
        _context.setViewport(width, height, 1.0f / Pixel.UnitRPX);
    }

    protected void onDrawFrame(GL10 gl10) {

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT );

        _context.exec();

        if(!_loaded && _context.loadingProgress() >= 1.0f) {
            _loaded = true;

            final WeakReference<GLViewElement> element = new WeakReference<>(this);

            handler.post(new Runnable() {
                @Override
                public void run() {
                    GLViewElement e = element.get();
                    if(e != null) {
                        Event ev = new Event(e);
                        ev.setData(e.data());
                        e._emit("load",ev);
                    }
                }
            });
        }
    }

    protected void installGLContext() {

        if(_context != null) {
            return ;
        }

        final WeakReference<GLViewElement> e = new WeakReference<GLViewElement>(this);

        _context = new Context(new GLSurfaceViewLooper((GLSurfaceView) this.view()), viewContext,_basePath);
        _context.on("app_*", new IScriptFunction() {
            @Override
            public int call() {

                final Context ctx = (Context) ScriptContext.currentContext();

                int top = ctx.getTop();

                if(top >1 && ctx.isObject(-top) && ctx.isString(-top +1)) {

                    final String name = ctx.toString(-top + 1);
                    Object data = null;

                    ctx.push("data");
                    ctx.getProp(-top -1);

                    if(ctx.isObject(-1)) {
                        data = ctx.toValue(-1);
                    }

                    ctx.pop();

                    //Log.d("kk","[APP] [EVENT] " + name);

                    GLViewElement element = e.get();

                    if(element != null  && name != null && name.startsWith("app_")) {

                        final Element.Event ev = new Element.Event(element);
                        ev.setData(data);

                        element.handler.post(new Runnable() {
                            @Override
                            public void run() {

                                GLViewElement element = e.get();

                                if(element != null) {
                                    element._emit(name.substring(4),ev);
                                }
                            }
                        });

                    }
                }

                return 0;
            }
        });

        GAProtocol.openContext(_context,this);

        _context.run();

        if(_width !=0 && _height != 0) {
            _context.setViewport(_width ,_height , 1.0f / Pixel.UnitRPX);
        }

    }

    protected void uninstallGLContext() {

        if(_context == null) {
            return ;
        }

        _context.recycle();
        _context = null;
        _loaded = false;

    }


    protected void _emit(String name, cn.kkmofang.view.event.Event event) {
        super.emit(name,event);
    }

    @Override
    public void emit(final String name, cn.kkmofang.view.event.Event event) {
        super.emit(name, event);

        if(event instanceof Element.Event && _context != null) {

            ((Element.Event) event).setCancelBubble(true);


            if("reopen".equals(name)) {
                GLSurfaceView view = (GLSurfaceView) this.view();

                if(view != null) {

                    final WeakReference<GLViewElement> element = new WeakReference<>(this);

                    view.queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            GLViewElement e = element.get();
                            if(e != null) {
                                e.uninstallGLContext();
                                e.installGLContext();
                            }
                        }
                    });

                }
                return;
            }

            final Object data = ((Element.Event)event).data();

            _context.post(new Runnable() {
                @Override
                public void run() {

                    Context ctx = (Context) ScriptContext.currentContext();
                    ctx.emit(name,data);

                }
            });
        }
    }

}
