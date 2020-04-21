package com.luomin.learnwidget.fill;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class FillBlankReplaceSpan extends ReplacementSpan {
    private final int DEFAULT_EMS_WIDTH = 12;
    private int index;
    private String needPaintText;
    private String answer;
    private float lineSpacingExtra;
    private boolean isFocusable, judgeIt;

    FillBlankReplaceSpan(int index, String needPaintText, String answer, float lineSpacingExtra) {
        this.index = index;
        this.needPaintText = needPaintText;
        this.answer = answer;
        this.lineSpacingExtra = lineSpacingExtra;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
        float defaultMinSize = paint.measureText(" ", 0, 1) * DEFAULT_EMS_WIDTH;
        float textActualWidth = paint.measureText(answer, 0, answer.length()) + 4;
        if (defaultMinSize > textActualWidth) {
            textActualWidth = defaultMinSize;
        }
        return (int) textActualWidth;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        //1.根据具体的宽度画线
        float stopX = x + getSize(paint, text, start, end, null);
        //3.额外 如果已经被判断了
        if (judgeIt) {
            if (!TextUtils.isEmpty(needPaintText) && answer.equals(needPaintText)) {
                int colorOriginal = paint.getColor();
                paint.setColor(Color.parseColor("#52b55e"));
                canvas.drawLine(x, bottom - lineSpacingExtra / 2, stopX, bottom - lineSpacingExtra / 2, paint);
                canvas.drawText(needPaintText, x + (stopX - x) / 2 - paint.measureText(needPaintText) / 2, y, paint);
                paint.setColor(colorOriginal);
            } else {
                int colorOriginal = paint.getColor();
                paint.setColor(Color.parseColor("#fd6360"));
                canvas.drawLine(x, bottom - lineSpacingExtra / 2, stopX, bottom - lineSpacingExtra / 2, paint);
                canvas.drawText(needPaintText, x + (stopX - x) / 2 - paint.measureText(needPaintText) / 2, y, paint);
                paint.setColor(colorOriginal);
            }
        } else {
            if (isFocusable) {
                int colorOriginal = paint.getColor();
                paint.setColor(Color.parseColor("#ff6800"));
                canvas.drawLine(x, bottom - lineSpacingExtra / 2, stopX, bottom - lineSpacingExtra / 2, paint);
                paint.setColor(colorOriginal);
            } else {
                canvas.drawLine(x, bottom - lineSpacingExtra / 2, stopX, bottom - lineSpacingExtra / 2, paint);
            }
            //2.画文字
            canvas.drawText(needPaintText, x + (stopX - x) / 2 - paint.measureText(needPaintText) / 2, y, paint);
        }
    }

    /**
     * 获取当前Span的矩形
     *
     * @param textView 该Span存在的TextView
     * @return 矩形
     */
    RectF drawSpanRectF(TextView textView) {
        RectF rectF = new RectF();

        Layout layout = textView.getLayout();
        Spannable text = (Spannable) textView.getText();
        int spanStart = text.getSpanStart(this);
        int spanEnd = text.getSpanEnd(this);
        int lineForOffsetStart = layout.getLineForOffset(spanStart);

        rectF.left = layout.getPrimaryHorizontal(spanStart);
        rectF.right = layout.getSecondaryHorizontal(spanEnd);
        Paint.FontMetrics fontMetrics = textView.getPaint().getFontMetrics();
        int line = layout.getLineBaseline(lineForOffsetStart);

        rectF.top = line + fontMetrics.ascent + fontMetrics.leading;
        rectF.bottom = line + fontMetrics.descent;

        return rectF;
    }

    int getIndex() {
        return index;
    }

    void setNeedPaintText(String needPaintText) {
        this.needPaintText = needPaintText;
    }

    void setFocusable(boolean focusable) {
        isFocusable = focusable;
    }

    public void setJudgeIt(boolean judgeIt) {
        this.judgeIt = judgeIt;
    }
}