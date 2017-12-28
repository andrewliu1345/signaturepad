package com.joesmate.signaturepad.views;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import com.joesmate.signaturepad.MResource;

import com.joesmate.signaturepad.utils.Bezier;
import com.joesmate.signaturepad.utils.ControlTimedPoints;
import com.joesmate.signaturepad.utils.SvgBuilder;
import com.joesmate.signaturepad.utils.TimedPoint;
import com.joesmate.signaturepad.view.ViewCompat;
import com.joesmate.signaturepad.view.ViewTreeObserverCompat;

import java.util.ArrayList;
import java.util.List;

public class SignaturePad extends View {

    List<List<float[]>> mSignData = new ArrayList<List<float[]>>();
    private List<float[]> mLineData;
    //View state
    private List<TimedPoint> mPoints = new ArrayList<TimedPoint>();
    private boolean mIsEmpty;
    private float mLastTouchX;
    private float mLastTouchY;
    private float mLastVelocity;
    private float mLastWidth;
    private RectF mDirtyRect;

    private final SvgBuilder mSvgBuilder = new SvgBuilder();

    // Cache
    private List<TimedPoint> mPointsCache = new ArrayList<TimedPoint>();
    private ControlTimedPoints mControlTimedPointsCached = new ControlTimedPoints();
    private Bezier mBezierCached = new Bezier();

    //Configurable parameters
    private int mMinWidth;
    private int mMaxWidth;
    private float mVelocityFilterWeight;
    private OnSignedListener mOnSignedListener;
    private boolean mClearOnDoubleClick;
    //Click values
    private long mFirstClick;
    private int mCountClick;
    private static final int DOUBLE_CLICK_DELAY_MS = 200;

    private static final float STROKE_WIDTH = 5f;

    /**
     * Need to track this so the dirty region can accommodate the stroke.
     **/
    private static final float HALF_STROKE_WIDTH = STROKE_WIDTH / 2;

    private Paint mPaint = new Paint();
    private TimedPoint tmpPoint;
    private Bitmap mSignatureBitmap = null;
    private Canvas mSignatureBitmapCanvas = null;

//    public SignaturePad(Context context) {
//        super(context);
//    }

    public SignaturePad(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (isInEditMode()) {
            return;
        }
        int[] SignaturePad = MResource.getIdsByName(this.getContext(), "styleable", "SignaturePad");
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                SignaturePad,
                0, 0);


        //Configurable parameters
        try {
            int SignaturePad_minWidth = MResource.getIdByName(this.getContext(), "styleable", "SignaturePad_minWidth");

            int SignaturePad_maxWidth = MResource.getIdByName(this.getContext(), "styleable", "SignaturePad_maxWidth");

            int SignaturePad_velocityFilterWeight = MResource.getIdByName(this.getContext(), "styleable", "SignaturePad_velocityFilterWeight");

            int SignaturePad_penColor = MResource.getIdByName(this.getContext(), "styleable", "SignaturePad_penColor");

            int SignaturePad_clearOnDoubleClick = MResource.getIdByName(this.getContext(), "styleable", "SignaturePad_clearOnDoubleClick");

            mMinWidth = a.getDimensionPixelSize(SignaturePad_minWidth, convertDpToPx(3));
            mMaxWidth = a.getDimensionPixelSize(SignaturePad_maxWidth, convertDpToPx(10));
            mVelocityFilterWeight = a.getFloat(SignaturePad_velocityFilterWeight, 10.0f);
            mPaint.setColor(a.getColor(SignaturePad_penColor, Color.BLACK));
            mClearOnDoubleClick = a.getBoolean(SignaturePad_clearOnDoubleClick, false);
        } finally {
            a.recycle();
        }

        //Fixed parameters
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);

        //Dirty rectangle to update only the changed portion of the view
        mDirtyRect = new RectF();

        clear();
    }

    public List<List<float[]>> getSignatureData() {

//        List<float[]> lineData = new ArrayList<float[]>();
//        int size=mPoints.size();
//        for (int i=0;i<size;i++)
//        {
//
//            lineData.add(new float[]{mPoints.get(i).x,mPoints.get(i).y,mPoints.get(i).w});
//            mSignData.add(lineData);
//            lineData.clear();
//        }
        return mSignData;

    }

    /**
     * Set the pen color from a given resource.
     * If the resource is not found, {@link android.graphics.Color#BLACK} is assumed.
     *
     * @param colorRes the color resource.
     */
    public void setPenColorRes(int colorRes) {
        try {
            setPenColor(getResources().getColor(colorRes));
        } catch (Resources.NotFoundException ex) {
            setPenColor(Color.parseColor("#000000"));
        }
    }

    /**
     * Set the pen color from a given color.
     *
     * @param color the color.
     */
    public void setPenColor(int color) {
        mPaint.setColor(color);
    }

    /**
     * Set the minimum width of the stroke in pixel.
     *
     * @param minWidth the width in dp.
     */
    public void setMinWidth(float minWidth) {
        mMinWidth = convertDpToPx(minWidth);
    }

    /**
     * Set the maximum width of the stroke in pixel.
     *
     * @param maxWidth the width in dp.
     */
    public void setMaxWidth(float maxWidth) {
        mMaxWidth = convertDpToPx(maxWidth);
    }

    /**
     * Set the velocity filter weight.
     *
     * @param velocityFilterWeight the weight.
     */
    public void setVelocityFilterWeight(float velocityFilterWeight) {
        mVelocityFilterWeight = velocityFilterWeight;
    }

    public void clear() {
        mSvgBuilder.clear();
        mPoints.clear();
        mSignData.clear();
        mLastVelocity = 0;
        mLastWidth = (mMinWidth + mMaxWidth) / 2;

        if (mSignatureBitmap != null) {
            mSignatureBitmap = null;
            ensureSignatureBitmap();
        }

        setIsEmpty(true);

        invalidate();
    }

    int painMun = 0;

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS)
            return super.onTouchEvent(event);
        if (!isEnabled())
            return false;
        // int eventA = (int) (255 * Math.max(0.01f, event.getPressure()));
        float eventX = event.getX();
        float eventY = event.getY();
//        if (event.getPressure() < 0.0009)
//            return false;
        float eventW = mMaxWidth * event.getPressure();
        eventW = Math.min(eventW, mMaxWidth);
        eventW = Math.max(mMinWidth, eventW);


        Log.i("x,y,w", String.format("x:%f,y:%f,w%f", eventX, eventY, eventW));

//        if (eventW < 0.1f)
//            eventW = 0.1f;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                this.mLineData = new ArrayList();
                painMun = 0;
                getParent().requestDisallowInterceptTouchEvent(true);
                mPoints.clear();
                if (isDoubleClick()) break;
                mLastTouchX = eventX;
                mLastTouchY = eventY;
                resetDirtyRect(eventX, eventY);

                tmpPoint = getNewPoint(eventX, eventY, eventW);
//                tmpPoint = getNewPoint(eventX, eventY, eventW);
                Log.i("ACTION_DOWN 压力", String.format("Pressure：%f", eventW));
                addPoint(tmpPoint);
                painMun++;

                if (mOnSignedListener != null) mOnSignedListener.onStartSigning();

            case MotionEvent.ACTION_MOVE:

                resetDirtyRect(eventX, eventY);
                //addPoint(tmpPoint);
//                float n = eventW/tmpPoint.w ;
//                if (n < 0.7f)
//                    eventW = (tmpPoint.w-mMinWidth)*0.2f/50;
                eventW = (1 - 0.55f) * eventW + 0.55f * tmpPoint.w;
                long a = System.currentTimeMillis() - tmpPoint.timestamp;
                tmpPoint = getNewPoint(eventX, eventY, eventW, (int) a);

                //tmpPoint = getNewPoint(eventX, eventY, eventW);
                Log.i("ACTION_MOVE 压力", String.format("Pressure：%f", eventW));
                addPoint(tmpPoint);
                painMun++;

                break;

            case MotionEvent.ACTION_UP:
                resetDirtyRect(eventX, eventY);
                Log.i("ACTION_UP 压力", String.format("Pressure：%f", eventW));

//                int pointsCount = mPoints.size();
//                if (pointsCount < 4) {
//
//                    for (int i = 4 - pointsCount; i > 1; i--) {
//                        addPoint(getNewPoint(eventX, eventY, eventW));
//                    }
//
//                } else {
//                    float start_w= mPoints.get(0).w;
//                    float wid_data=mPoints.get(3).w-start_w;
//
//                    for (int i = 4 - pointsCount; i > 1; i--) {
//                        float t=((float) i) /pointsCount;
//                        t*=t*t;
//                         TimedPoint tp=mPoints.get(i);
//                        tp.w=start_w+t*wid_data;
//                        mPoints.set(i,tp);
//                    }
//                }
                addPoint(getNewPoint(eventX, eventY, eventW));
                painMun++;
                getParent().requestDisallowInterceptTouchEvent(true);
                setIsEmpty(false);

                Log.i("点数:", String.format("Pressure：%d", painMun));
                mSignData.add(mLineData);
                break;

            default:
                return false;
        }

        // Include half the stroke width to avoid clipping.
        invalidate(
                (int) (mDirtyRect.left - eventW),
                (int) (mDirtyRect.top - eventW),
                (int) (mDirtyRect.right + eventW),
                (int) (mDirtyRect.bottom + eventW));

        mLastTouchX = eventX;
        mLastTouchY = eventY;

        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mSignatureBitmap != null) {
            canvas.drawBitmap(mSignatureBitmap, 0, 0, mPaint);
        }
    }

    public void setOnSignedListener(OnSignedListener listener) {
        mOnSignedListener = listener;
    }

    public boolean isEmpty() {
        return mIsEmpty;
    }

    public String getSignatureSvg() {
        int width = getTransparentSignatureBitmap().getWidth();
        int height = getTransparentSignatureBitmap().getHeight();
        return mSvgBuilder.build(width, height);
    }

    public Bitmap getSignatureBitmap() {
        Bitmap originalBitmap = getTransparentSignatureBitmap();
        Bitmap whiteBgBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(whiteBgBitmap);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(originalBitmap, 0, 0, null);
        return whiteBgBitmap;
    }

    public void setSignatureBitmap(final Bitmap signature) {
        // View was laid out...
        if (ViewCompat.isLaidOut(this)) {
            clear();
            ensureSignatureBitmap();

            RectF tempSrc = new RectF();
            RectF tempDst = new RectF();

            int dWidth = signature.getWidth();
            int dHeight = signature.getHeight();
            int vWidth = getWidth();
            int vHeight = getHeight();

            // Generate the required transform.
            tempSrc.set(0, 0, dWidth, dHeight);
            tempDst.set(0, 0, vWidth, vHeight);

            Matrix drawMatrix = new Matrix();
            drawMatrix.setRectToRect(tempSrc, tempDst, Matrix.ScaleToFit.CENTER);

            Canvas canvas = new Canvas(mSignatureBitmap);
            canvas.drawBitmap(signature, drawMatrix, null);
            setIsEmpty(false);
            invalidate();
        }
        // View not laid out yet e.g. called from onCreate(), onRestoreInstanceState()...
        else {
            getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    // Remove layout listener...
                    ViewTreeObserverCompat.removeOnGlobalLayoutListener(getViewTreeObserver(), this);

                    // Signature bitmap...
                    setSignatureBitmap(signature);
                }
            });
        }
    }

    public Bitmap getTransparentSignatureBitmap() {
        ensureSignatureBitmap();
        return mSignatureBitmap;
    }

    public Bitmap getTransparentSignatureBitmap(boolean trimBlankSpace) {

        if (!trimBlankSpace) {
            return getTransparentSignatureBitmap();
        }

        ensureSignatureBitmap();

        int imgHeight = mSignatureBitmap.getHeight();
        int imgWidth = mSignatureBitmap.getWidth();

        int backgroundColor = Color.TRANSPARENT;

        int xMin = Integer.MAX_VALUE,
                xMax = Integer.MIN_VALUE,
                yMin = Integer.MAX_VALUE,
                yMax = Integer.MIN_VALUE;

        boolean foundPixel = false;

        // Find xMin
        for (int x = 0; x < imgWidth; x++) {
            boolean stop = false;
            for (int y = 0; y < imgHeight; y++) {
                if (mSignatureBitmap.getPixel(x, y) != backgroundColor) {
                    xMin = x;
                    stop = true;
                    foundPixel = true;
                    break;
                }
            }
            if (stop)
                break;
        }

        // Image is empty...
        if (!foundPixel)
            return null;

        // Find yMin
        for (int y = 0; y < imgHeight; y++) {
            boolean stop = false;
            for (int x = xMin; x < imgWidth; x++) {
                if (mSignatureBitmap.getPixel(x, y) != backgroundColor) {
                    yMin = y;
                    stop = true;
                    break;
                }
            }
            if (stop)
                break;
        }

        // Find xMax
        for (int x = imgWidth - 1; x >= xMin; x--) {
            boolean stop = false;
            for (int y = yMin; y < imgHeight; y++) {
                if (mSignatureBitmap.getPixel(x, y) != backgroundColor) {
                    xMax = x;
                    stop = true;
                    break;
                }
            }
            if (stop)
                break;
        }

        // Find yMax
        for (int y = imgHeight - 1; y >= yMin; y--) {
            boolean stop = false;
            for (int x = xMin; x <= xMax; x++) {
                if (mSignatureBitmap.getPixel(x, y) != backgroundColor) {
                    yMax = y;
                    stop = true;
                    break;
                }
            }
            if (stop)
                break;
        }

        return Bitmap.createBitmap(mSignatureBitmap, xMin, yMin, xMax - xMin, yMax - yMin);
    }

    private boolean isDoubleClick() {
        if (mClearOnDoubleClick) {
            if (mFirstClick != 0 && System.currentTimeMillis() - mFirstClick > DOUBLE_CLICK_DELAY_MS) {
                mCountClick = 0;
            }
            mCountClick++;
            if (mCountClick == 1) {
                mFirstClick = System.currentTimeMillis();
            } else if (mCountClick == 2) {
                long lastClick = System.currentTimeMillis();
                if (lastClick - mFirstClick < DOUBLE_CLICK_DELAY_MS) {
                    this.clear();
                    return true;
                }
            }
        }
        return false;
    }

    private TimedPoint getNewPoint(float x, float y, float w, int a) {
        int mCacheSize = mPointsCache.size();
        TimedPoint timedPoint;
        if (mCacheSize == 0) {
            // Cache is empty, create a new point
            timedPoint = new TimedPoint();
        } else {
            // Get point from cache
            timedPoint = mPointsCache.remove(mCacheSize - 1);
        }

        return timedPoint.set(x, y, w, a);
    }

    private TimedPoint getNewPoint(float x, float y, float w) {
        int mCacheSize = mPointsCache.size();
        TimedPoint timedPoint;
        if (mCacheSize == 0) {
            // Cache is empty, create a new point
            timedPoint = new TimedPoint();
        } else {
            // Get point from cache
            timedPoint = mPointsCache.remove(mCacheSize - 1);
        }

        return timedPoint.set(x, y, w);
    }

    private TimedPoint getNewPoint(float x, float y) {
        int mCacheSize = mPointsCache.size();
        TimedPoint timedPoint;
        if (mCacheSize == 0) {
            // Cache is empty, create a new point
            timedPoint = new TimedPoint();
        } else {
            // Get point from cache
            timedPoint = mPointsCache.remove(mCacheSize - 1);
        }

        return timedPoint.set(x, y);
    }

    private void recyclePoint(TimedPoint point) {
        mPointsCache.add(point);
    }

    private void addPoint(TimedPoint newPoint) {
        mPoints.add(newPoint);

        int pointsCount = mPoints.size();

        if (pointsCount > 3) {
            Log.i("pointsCount：", "" + pointsCount);
            ControlTimedPoints tmp = calculateCurveControlPoints(mPoints.get(0), mPoints.get(1), mPoints.get(2));
            TimedPoint c2 = tmp.c2;
            recyclePoint(tmp.c1);

            tmp = calculateCurveControlPoints(mPoints.get(1), mPoints.get(2), mPoints.get(3));
            TimedPoint c3 = tmp.c1;
            recyclePoint(tmp.c2);

            Bezier curve = mBezierCached.set(mPoints.get(1), c2, c3, mPoints.get(2));

            TimedPoint startPoint = curve.startPoint;
            TimedPoint endPoint = curve.endPoint;
//
//            float velocity = endPoint.velocityFrom(startPoint);
//            velocity = Float.isNaN(velocity) ? 0.0f : velocity;

//            float velocity = mVelocityFilterWeight * startPoint.w
//                    + (1 - mVelocityFilterWeight) * endPoint.w;
//
//            // The new width is a function of the velocity. Higher velocities
//            // correspond to thinner strokes.
//            float newWidth = strokeWidth(velocity);

            // The Bezier's width starts out as last curve's final width, and
            // gradually changes to the stroke width just calculated. The new
            // width calculation is based on the velocity between the Bezier's
            // start and end mPoints.
            // linearSmooth3(curve, 4);
            addBezier(curve, startPoint.w, endPoint.w);

//            mLastVelocity = velocity;
//            mLastWidth = newWidth;

            // Remove the first element from the list,
            // so that we always have no more than 4 mPoints in mPoints array.
            recyclePoint(mPoints.remove(0));

            recyclePoint(c2);
            recyclePoint(c3);

        } else if (pointsCount == 1) {
            // To reduce the initial lag make it work with 3 mPoints
            // by duplicating the first point
            TimedPoint firstPoint = mPoints.get(0);
            mPoints.add(getNewPoint(firstPoint.x, firstPoint.y, firstPoint.w));
        }
    }


    private void addBezier(Bezier curve, float startWidth, float endWidth) {
        mSvgBuilder.append(curve, (startWidth + endWidth) / 2);
        ensureSignatureBitmap();
//        float n = startWidth / endWidth;
//        if (n < 1.5f) {
//            endWidth = startWidth / 1.2f;
//        }
        float widthDelta = endWidth - startWidth;
        int drawSteps = (int) Math.floor(curve.length());

        float x = curve.startPoint.x;
        float y = curve.startPoint.y;
        float w = curve.startPoint.w;
        int a = curve.startPoint.a;
        long ts = curve.startPoint.timestamp;//开始时间挫
        long te = curve.endPoint.timestamp;//结束时间挫
        long td = te - ts;

        Log.i("drawSteps：", "" + drawSteps);
        if (drawSteps == 0) {
            mPaint.setStrokeWidth(w);
            mSignatureBitmapCanvas.drawPoint(x, y, mPaint);

           // this.mLineData.add(new float[]{x, y, w});
            this.mLineData.add(new float[]{x, y, w,a});

        } else {
            int av = (int) td / drawSteps;
            for (int i = 0; i < drawSteps; i++) {
                // Calculate the Bezier (x, y) coordinate for this step.
                float t = ((float) i) / drawSteps;
                float tt = t * t;
                float ttt = tt * t;
                float u = 1 - t;
                float uu = u * u;
                float uuu = uu * u;

                x = uuu * curve.startPoint.x;
                x += 3 * uu * t * curve.control1.x;
                x += 3 * u * tt * curve.control2.x;
                x += ttt * curve.endPoint.x;

                y = uuu * curve.startPoint.y;
                y += 3 * uu * t * curve.control1.y;
                y += 3 * u * tt * curve.control2.y;
                y += ttt * curve.endPoint.y;


                w = startWidth + ttt * widthDelta;

                // startWidth = w - ttt * widthDelta;
                //w = startWidth + ttt * widthDelta;
//                w = uuu * curve.startPoint.w;
//                w += 3 * uu * t * curve.control1.w;
//                w += 3 * u * tt * curve.control2.w;
                //w += ttt * curve.endPoint.w;

                // Set the incremental stroke width and draw.
                mPaint.setStrokeWidth(w);
                mSignatureBitmapCanvas.drawPoint(x, y, mPaint);


                //this.mLineData.add(new float[]{x, y, w});

                this.mLineData.add(new float[]{x, y, w,av});
            }

        }
        expandDirtyRect(x, y);
        Log.i("画点：", ".............................................");
        // mPaint.setStrokeWidth(originalWidth);
    }

    private void addBezier(Bezier curve, float startWidth, float endWidth, int startAlpha, int endAlpha) {
        mSvgBuilder.append(curve, (startWidth + endWidth) / 2);
        ensureSignatureBitmap();
        //   float originalWidth = mPaint.getStrokeWidth();
        //   float n = startWidth / endWidth;
//        if (n>1.5f)
//            endWidth = startWidth / 1.2f;
        float widthDelta = endWidth - startWidth;
        int alphaData = endAlpha - startAlpha;
        int drawSteps = (int) Math.floor(curve.length());

        float x = curve.startPoint.x;
        float y = curve.startPoint.y;
        float w = curve.startPoint.w;
        int a = curve.startPoint.a;

        Log.i("drawSteps：", "" + drawSteps);
        if (drawSteps == 0) {
            mPaint.setStrokeWidth(w);
            mSignatureBitmapCanvas.drawPoint(x, y, mPaint);
//            expandDirtyRect(x, y);
        } else {
            for (int i = 0; i < drawSteps; i++) {
                // Calculate the Bezier (x, y) coordinate for this step.
                float t = ((float) i) / drawSteps;
                float tt = t * t;
                float ttt = tt * t;
                float u = 1 - t;
                float uu = u * u;
                float uuu = uu * u;

                x = uuu * curve.startPoint.x;
                x += 3 * uu * t * curve.control1.x;
                x += 3 * u * tt * curve.control2.x;
                x += ttt * curve.endPoint.x;

                y = uuu * curve.startPoint.y;
                y += 3 * uu * t * curve.control1.y;
                y += 3 * u * tt * curve.control2.y;
                y += ttt * curve.endPoint.y;

//                float n = startWidth / widthDelta;
//                if (n < 1.5f) {
//                    w = startWidth / 1.2f;
//                } else {
                w = startWidth + ttt * widthDelta;
//                }
                //a = (int) (startAlpha + ttt * alphaData);
//                w = uuu * curve.startPoint.w;
//                w += 3 * uu * t * curve.control1.w;
//                w += 3 * u * tt * curve.control2.w;
//                w += ttt * curve.endPoint.w;

                // Set the incremental stroke width and draw.
                mPaint.setStrokeWidth(w);
                mPaint.setAlpha(a);
                mSignatureBitmapCanvas.drawPoint(x, y, mPaint);


            }

        }
        expandDirtyRect(x, y);
        Log.i("画点：", ".............................................");
        // mPaint.setStrokeWidth(originalWidth);
    }

    private ControlTimedPoints calculateCurveControlPoints(TimedPoint s1, TimedPoint s2, TimedPoint s3) {
        float dx1 = s1.x - s2.x;
        float dy1 = s1.y - s2.y;
        float dx2 = s2.x - s3.x;
        float dy2 = s2.y - s3.y;

        float m1X = (s1.x + s2.x) / 2.0f;
        float m1Y = (s1.y + s2.y) / 2.0f;
        float m2X = (s2.x + s3.x) / 2.0f;
        float m2Y = (s2.y + s3.y) / 2.0f;

        float l1 = (float) Math.sqrt(dx1 * dx1 + dy1 * dy1);
        float l2 = (float) Math.sqrt(dx2 * dx2 + dy2 * dy2);

        float dxm = (m1X - m2X);
        float dym = (m1Y - m2Y);
        float k = l2 / (l1 + l2);
        if (Float.isNaN(k)) k = 0.0f;
        float cmX = m2X + dxm * k;
        float cmY = m2Y + dym * k;

        float tx = s2.x - cmX;
        float ty = s2.y - cmY;

        return mControlTimedPointsCached.set(getNewPoint(m1X + tx, m1Y + ty), getNewPoint(m2X + tx, m2Y + ty));
    }

    private float strokeWidth(float velocity) {
        return Math.max(mMaxWidth / (velocity + 1), mMinWidth);
    }

    /**
     * Called when replaying history to ensure the dirty region includes all
     * mPoints.
     *
     * @param historicalX the previous x coordinate.
     * @param historicalY the previous y coordinate.
     */
    private void expandDirtyRect(float historicalX, float historicalY) {
        if (historicalX < mDirtyRect.left) {
            mDirtyRect.left = historicalX;
        } else if (historicalX > mDirtyRect.right) {
            mDirtyRect.right = historicalX;
        }
        if (historicalY < mDirtyRect.top) {
            mDirtyRect.top = historicalY;
        } else if (historicalY > mDirtyRect.bottom) {
            mDirtyRect.bottom = historicalY;
        }
    }

    /**
     * Resets the dirty region when the motion event occurs.
     *
     * @param eventX the event x coordinate.
     * @param eventY the event y coordinate.
     */
    private void resetDirtyRect(float eventX, float eventY) {

        // The mLastTouchX and mLastTouchY were set when the ACTION_DOWN motion event occurred.
        mDirtyRect.left = Math.min(mLastTouchX, eventX);
        mDirtyRect.right = Math.max(mLastTouchX, eventX);
        mDirtyRect.top = Math.min(mLastTouchY, eventY);
        mDirtyRect.bottom = Math.max(mLastTouchY, eventY);
    }

    private void setIsEmpty(boolean newValue) {
        mIsEmpty = newValue;
        if (mOnSignedListener != null) {
            if (mIsEmpty) {
                mOnSignedListener.onClear();
            } else {
                mOnSignedListener.onSigned();
            }
        }
    }

    private void ensureSignatureBitmap() {
        if (mSignatureBitmap == null) {
            mSignatureBitmap = Bitmap.createBitmap(getWidth(), getHeight(),
                    Bitmap.Config.ARGB_8888);
            mSignatureBitmapCanvas = new Canvas(mSignatureBitmap);
        }
    }

    private int convertDpToPx(float dp) {
        return Math.round(getContext().getResources().getDisplayMetrics().density * dp);
    }

    public interface OnSignedListener {
        void onStartSigning();

        void onSigned();

        void onClear();
    }

    void linearSmooth3(Bezier _bezier, int N) {
        float[] out = new float[4];
        float[] in = new float[4];
        in[0] = _bezier.startPoint.w;
        in[1] = _bezier.control1.w;
        in[2] = _bezier.control2.w;
        in[3] = _bezier.endPoint.w;

        out[0] = in[0];

        for (int i = 1; i <= N - 2; i++) {
            out[i] = (in[i - 1] + in[i] + in[i + 1]) / 3.0f;
        }

        out[N - 1] = in[N - 1];
        _bezier.startPoint.w = out[0];
        _bezier.control1.w = out[1];
        _bezier.control2.w = out[2];
        _bezier.endPoint.w = out[2];
    }
}
