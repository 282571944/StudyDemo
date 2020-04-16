package com.luomin.learnwidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * @see #onMeasure(int, int)
 * <p>
 */
public class DoubleSeekBar extends View {
    //1
    int minValue = 0;
    //100
    int maxValue = 100;
    //用于选择
    int curMinValue = minValue;
    int curMaxValue = maxValue;

    //线条宽度
    int lineWidth;
    //默认线条颜色
    int colorDefault = Color.parseColor("#E5E6E6");
    //选中线条颜色
    int colorSel = Color.parseColor("#ff6800");
    int colorText = Color.WHITE;
    int colorDefaultText = Color.parseColor("#808080");
    //左标 和 右标
    Bitmap markBitmap;
    Bitmap markBitmapDown;
    //标志数字的颜色 和 刻度数字颜色的画笔
    Paint numbPaint;
    Paint numbDefaultPaint;
    //选择条的背景 和 选择后的背景的画笔
    Paint defaultLinePaint;
    Paint linePaint;
    //标志的画笔
    Paint bitmapPaint;
    private Rect markerSrcRect;
    //监听器
    public MoveListener moveListener;
    public ChangedListener changedListener;

    public DoubleSeekBar(Context context) {
        super(context);
        init();
    }

    public DoubleSeekBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DoubleSeekBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * 1.初始化线宽
     * 2.初始化按钮的图形
     * 3.初始化 数字画笔 线画笔 默认画笔 图形画笔
     */
    private void init() {
        lineWidth = dp2px(5);

        BitmapDrawable bm = (BitmapDrawable) getContext().getResources().getDrawable(R.drawable.water);
        markBitmap = bm.getBitmap();
        markerSrcRect = new Rect(0, 0, bm.getIntrinsicWidth(), bm.getIntrinsicHeight());

        bm = (BitmapDrawable) getContext().getResources().getDrawable(R.drawable.water_c);
        markBitmapDown = bm.getBitmap();

        numbPaint = new Paint();
        numbPaint.setDither(true);
        numbPaint.setAntiAlias(true);
        numbPaint.setColor(colorText);
        numbPaint.setTextSize(dp2px(12));

        numbDefaultPaint = new Paint();
        numbDefaultPaint.setDither(true);
        numbDefaultPaint.setAntiAlias(true);
        numbDefaultPaint.setColor(colorDefaultText);
        numbDefaultPaint.setTextSize(dp2px(10));

        linePaint = new Paint();
        linePaint.setDither(true);
        linePaint.setAntiAlias(true);
        linePaint.setColor(colorSel);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeWidth(lineWidth);

        defaultLinePaint = new Paint();
        defaultLinePaint.setStrokeWidth(lineWidth);
        defaultLinePaint.setDither(true);
        defaultLinePaint.setAntiAlias(true);
        defaultLinePaint.setStrokeCap(Paint.Cap.ROUND);
        defaultLinePaint.setColor(colorDefault);

        bitmapPaint = new Paint();
        bitmapPaint.setDither(true);
        bitmapPaint.setAntiAlias(true);
        bitmapPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));

    }

    /**
     * 1.预览的时候不绘制
     * 2.画线
     *
     * @param canvas
     */
    @Override
    protected void onDraw(Canvas canvas) {
        if (isInEditMode()) {
            //预览的时候不绘制
            return;
        }
        super.onDraw(canvas);
        drawLine(canvas);
    }

    //在drawLine中计算
    //线段长度
    float lineLength = 0F;
    //线段开始X
    float lineStartX;
    //线段开始Y
    float lineStartY;
    //记录当前 最大最小操作点位置
    Rect leftMoveRect;
    Rect rightMoveRect;

    /**
     * 一个数值所占用的px宽度
     */
    float spaceValuePx;

    /**
     * 1.画线
     * 2.画指示
     * 3.画文字
     * 4.
     *
     * @param canvas
     */
    private void drawLine(Canvas canvas) {
        lineLength = getWidth();
        //为最左最右 留出文字和mark空间
        lineStartX = dp2px(40);
        lineLength -= lineStartX * 2;
        spaceValuePx = lineLength / 100;
        //减去1刻度,因为实际上是 99段
        lineStartX += spaceValuePx / 2;
        lineLength -= spaceValuePx;
        float height = getHeight();

        lineStartY = height / 2 - lineWidth / 2;

        canvas.drawLine(lineStartX, lineStartY, lineStartX + lineLength, lineStartY, defaultLinePaint);
        float selStartX = lineStartX + (curMinValue - 1) * spaceValuePx;

        float selEndX = lineStartX + (curMaxValue - 1) * spaceValuePx;
        canvas.drawLine(selStartX, lineStartY, selEndX, lineStartY, linePaint);
        //画指示点
        float pointEndY = (lineStartY) + dp2px(10) / 2;

        // 画下面指示器
        float markerMarginTop = dp2px(5);
        float[] size = new float[]{dp2px(22), dp2px(26)};
        Rect leftTargetRect = new Rect((int) (selStartX - size[0] / 2), (int) (pointEndY + markerMarginTop),
                (int) (selStartX + size[0] / 2),
                (int) (pointEndY + markerMarginTop + size[1]));

        {
            //可触摸区域
            leftMoveRect = new Rect(leftTargetRect);
            leftMoveRect.left = leftMoveRect.left + 10;
            leftMoveRect.right = leftMoveRect.right + 10;
            leftMoveRect.bottom = getHeight();
        }
        Rect textLeftRect = new Rect(leftTargetRect);

        float v = numbPaint.measureText("10");
        //画刻度
        for (int i = 0; i <= 10; i++) {
            canvas.drawText(i * 10 + "", (lineStartX + spaceValuePx * i * 10) - v / 2, textLeftRect.centerY() + dp2px(6), numbDefaultPaint);
        }

        canvas.drawBitmap(markBitmap, markerSrcRect, leftTargetRect, bitmapPaint);

        v = numbPaint.measureText(curMinValue + "");
        canvas.drawText(curMinValue + "", textLeftRect.centerX() - v / 2, textLeftRect.centerY() + dp2px(6), numbPaint);

        Rect rightTargetRect = new Rect((int) (selEndX + lineWidth / 2 - size[0] / 2), (int) (pointEndY - size[1] - 2 * markerMarginTop),
                (int) (selEndX + lineWidth / 2 + size[0] / 2),
                (int) (pointEndY - 2 * markerMarginTop));
        {   //可触摸区域
            rightMoveRect = new Rect(rightTargetRect);
            rightMoveRect.left = rightMoveRect.left + 10;
            rightMoveRect.right = rightMoveRect.right + 10;
            rightMoveRect.bottom = getHeight();
        }

        canvas.drawBitmap(markBitmapDown, markerSrcRect, rightTargetRect, bitmapPaint);
        Rect textRightRect = new Rect(rightTargetRect);
        v = numbPaint.measureText(curMaxValue + "");
        canvas.drawText(curMaxValue + "", textRightRect.centerX() - v / 2, textRightRect.centerY() + dp2px(3), numbPaint);
    }


    /**
     * 1.多于触摸两点就返回
     * 2.如果当前视图可用则继续
     * 3.触摸情况
     * 1.如果是按下 如果按中操作点就消费掉
     * 2.如果是移动 当前为右键   则计算当前x坐标到进度最开始坐标的位置后计算出当前位置的文本数值 如果已经是最小值或者最大值则不更新当前  并返回最小和最大值
     * 3.如果是抬起 回调当前的最终结果
     *
     * @param event
     * @return
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() > 1) {
            return super.onTouchEvent(event);
        }
        if (isEnabled()) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (checkEventType(event)) {
                        return true;
                    } else {
                        return super.onTouchEvent(event);
                    }
                case MotionEvent.ACTION_MOVE:
                    float x = event.getX();
                    if (eventType == 1) {
                        int numb = (int) ((x - lineStartX) / spaceValuePx);
                        if (numb != curMaxValue) {
                            if (numb <= curMinValue) {
                                return true;
                            }
                            if (numb > maxValue) {
                                return true;
                            }
                            curMaxValue = numb;
                            if (moveListener != null) {
                                moveListener.move(curMinValue, curMaxValue);
                            }
                            invalidate();
                        }
                    } else {
                        int numb = (int) ((x - lineStartX) / spaceValuePx);
                        if (numb != curMaxValue) {
                            if (numb >= curMaxValue) {
                                return true;
                            }
                            if (numb < minValue) {
                                return true;
                            }
                            curMinValue = numb;
                            if (moveListener != null) {
                                moveListener.move(curMinValue, curMaxValue);
                            }
                            invalidate();
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (changedListener != null) {
                        changedListener.changed(curMinValue, curMaxValue);
                    }
                    break;
            }
            return super.onTouchEvent(event);
        } else {
            return super.onTouchEvent(event);
        }
    }

    int eventType;

    /**
     * @param event
     * @return -1 没有击中操作点,0 最小值,1最大值
     */
    public boolean checkEventType(MotionEvent event) {
        if (curMinValue > 90) {
            if (leftMoveRect.contains((int) event.getX() + 1, (int) event.getY() + 1)) {
                eventType = 0;
                return true;
            }

            if (rightMoveRect.contains((int) event.getX() + 1, (int) event.getY() + 1)) {
                eventType = 1;
                return true;
            }
        } else {
            if (rightMoveRect.contains((int) event.getX() + 1, (int) event.getY() + 1)) {
                eventType = 1;
                return true;
            }
            if (leftMoveRect.contains((int) event.getX() + 1, (int) event.getY() + 1)) {
                eventType = 0;
                return true;
            }
        }

        eventType = -1;
        return false;
    }

    public void setCurMinValue(int curMinValue) {
        this.curMinValue = curMinValue;
        postInvalidate();
    }

    public void setCurMaxValue(int curMaxValue) {
        this.curMaxValue = curMaxValue;
        postInvalidate();
    }


    public int getCurMaxValue() {
        return curMaxValue;
    }

    public int getCurMinValue() {
        return curMinValue;
    }

    public void setMoveListener(MoveListener moveListener) {
        this.moveListener = moveListener;
    }

    public void setChangedListener(ChangedListener changedListener) {
        this.changedListener = changedListener;
    }

    /**
     * 移动中
     */
    public interface MoveListener {
        void move(int min, int max);
    }

    /**
     * 结束滑动后
     */
    public interface ChangedListener {
        void changed(int min, int max);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            //wrap_content 的时候 处理一下,设置为最低高度
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp2px(80), MeasureSpec.EXACTLY));
            return;
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
        //防止绘制不全,最低高度为80dp
        if (getMeasuredHeight() < dp2px(80)) {
            setMeasuredDimension(getMeasuredWidth(), dp2px(80));
        }
    }

    public int dp2px(float dpVal) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                dpVal, getContext().getResources().getDisplayMetrics());
    }

}