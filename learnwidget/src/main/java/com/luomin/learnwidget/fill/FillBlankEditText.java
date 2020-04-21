package com.luomin.learnwidget.fill;

import android.content.Context;
import android.graphics.Color;
import android.graphics.RectF;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 填空题
 */
public class FillBlankEditText extends RelativeLayout {
    private TextView displayTextView;
    private EditText fillBlankEditText;
    private int textSize = 16;
    private int textColor = Color.parseColor("#414141");
    private int editTextColor = Color.parseColor("#ff6800");
    private SparseArray<String> fillContents;
    private SparseArray<FillBlankReplaceSpan> fillContentsSpans;
    private boolean canEdit = true, judgeIt;

    public FillBlankEditText(Context context) {
        super(context);
        initFillBlankEditText(context);
    }

    public FillBlankEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        initFillBlankEditText(context);
    }

    public FillBlankEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initFillBlankEditText(context);
    }

    /**
     * 1.添加TextView
     * 2.添加EditText
     * 3.设置TextView需要填空的地方
     * 4.设置TextView的点击事件
     * 5.初始化当前填空内容
     */
    private void initFillBlankEditText(Context context) {
        //1.添加TextView
        displayTextView = new TextView(context);
        displayTextView.setTextSize(textSize);
        displayTextView.setTextColor(textColor);
        addView(displayTextView, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        //2.添加EditText
        fillBlankEditText = new EditText(context);
        fillBlankEditText.setVisibility(GONE);
        fillBlankEditText.setGravity(Gravity.CENTER);
        fillBlankEditText.setSingleLine(true);
        fillBlankEditText.setTextSize(textSize);
        fillBlankEditText.setPadding(0, 0, 0, 0);
        fillBlankEditText.setBackgroundDrawable(null);
        fillBlankEditText.setTextColor(editTextColor);
        addView(fillBlankEditText, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        //5.初始化当前填空内容
        fillContents = new SparseArray<>();
    }

    /**
     * 1.初始化当前显示文字
     * 2.找出需要填空的索引位置 List
     * 3.覆盖所需要填空表现的位置
     * 4.设置TextView的点击事件
     *
     * @param sourceText 源文本
     * @param regex      被标志为填空的文本
     */
    public void setDisplayText(String sourceText, String regex, @NonNull List<String> answers) {
        //1.初始化当前显示文字
        SpannableString spannableString = new SpannableString(sourceText);
        //2.找出需要填空的索引位置 List
        Pattern compile = Pattern.compile(regex);
        Matcher matcher = compile.matcher(sourceText);
        List<FillStart2End> sourcePositions = new ArrayList<>();
        while (matcher.find()) {
            sourcePositions.add(new FillStart2End(matcher.start(), matcher.end()));
        }
        //3.覆盖所需要填空表现的位置
        fillContentsSpans = new SparseArray<>();
        for (int i = 0; i < sourcePositions.size(); i++) {
            FillStart2End fillStart2End = sourcePositions.get(i);
            FillBlankReplaceSpan fillBlankReplaceSpan;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                fillBlankReplaceSpan = new FillBlankReplaceSpan(i, fillContents.get(i) != null ? fillContents.get(i) : "", answers.size() > i ? null2EmptyStr(answers.get(i)) : " ", displayTextView.getLineSpacingExtra());
            } else {
                fillBlankReplaceSpan = new FillBlankReplaceSpan(i, fillContents.get(i) != null ? fillContents.get(i) : "", answers.size() > i ? null2EmptyStr(answers.get(i)) : " ", 0);
            }
            spannableString.setSpan(fillBlankReplaceSpan, fillStart2End.getStart(), fillStart2End.getEnd(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            fillContentsSpans.put(i, fillBlankReplaceSpan);
        }

        displayTextView.setText(spannableString);
        //4.设置TextView的点击事件
        displayTextView.setMovementMethod(linkMovementMethod);
    }

    /**
     * 是否可以编辑
     *
     * @param canEdit 是否编辑
     */
    public void setCanEdit(boolean canEdit) {
        this.canEdit = canEdit;
    }

    /**
     * 设置是否判断对错
     *
     * @param judgeIt 是否判断对错
     */
    public void setJudgeIt(boolean judgeIt) {
        this.judgeIt = judgeIt;
        for (int i = 0; i < fillContentsSpans.size(); i++) {
            fillContentsSpans.get(i).setJudgeIt(judgeIt);
        }
        if (judgeIt) {
            fillBlankEditText.clearFocus();
            setCanEdit(false);
        }
        displayTextView.postInvalidate();
    }

    /**
     * 设置用户的回答并刷新界面
     *
     * @param userAnswers 用户回答
     */
    public void setFillContents(SparseArray<String> userAnswers) {
        this.fillContents = userAnswers;
        for (int i = 0; i < fillContentsSpans.size(); i++) {
            fillContentsSpans.get(i).setNeedPaintText(fillContents.get(i) != null ? fillContents.get(i) : "");
        }
        setJudgeIt(true);
    }


    /**
     * 重置当前答题内容
     */
    public void reset() {
        fillContents.clear();
        setCanEdit(true);
        fillBlankEditText.clearFocus();
        for (int i = 0; i < fillContentsSpans.size(); i++) {
            fillContentsSpans.get(i).setJudgeIt(false);
            fillContentsSpans.get(i).setNeedPaintText("");
        }
        displayTextView.postInvalidate();
    }

    /**
     * 存储需要填空的前后位置
     */
    class FillStart2End {
        private int start;
        private int end;

        FillStart2End(int start, int end) {
            this.start = start;
            this.end = end;
        }

        int getStart() {
            return start;
        }

        int getEnd() {
            return end;
        }
    }

    /**
     * 点击事件
     */
    public LinkMovementMethod linkMovementMethod = new LinkMovementMethod() {
        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    break;
                case MotionEvent.ACTION_UP:
                    //1.获取当前行号
                    float y = event.getY();
                    y -= widget.getPaddingTop();
                    y += widget.getScrollY();
                    Layout layout = widget.getLayout();
                    int lineNum = layout.getLineForVertical((int) y);
                    //2.获取当前字符量位置
                    float x = event.getX();
                    x -= widget.getPaddingLeft();
                    x += widget.getScrollX();
                    int currentLineCharOffset = layout.getOffsetForHorizontal(lineNum, x);
                    //3.获取当前偏移位置的Span
                    FillBlankReplaceSpan[] spans = buffer.getSpans(currentLineCharOffset, currentLineCharOffset, FillBlankReplaceSpan.class);
                    if (spans.length == 0) break;
                    FillBlankReplaceSpan fillBlankReplaceSpan = spans[0];
                    //4.处理点击事件
                    if (canEdit) {
                        setEditTextPosition(fillBlankReplaceSpan.getIndex(), fillBlankReplaceSpan.drawSpanRectF(widget));
                    }
                    break;
                default:
                    break;
            }

            return super.onTouchEvent(widget, buffer, event);
        }
    };

    /**
     * 0.防止文字被串改 (很重要,重置编辑框文字的时候会篡改上一次的位置为当前位置内容)
     * 1.设置当前编辑框的位置
     * 2.设置当前显示
     * 3.获取焦点,弹出软键盘
     *
     * @param index 当前编辑框的索引位置
     * @param rectF 编辑框的位置
     */
    private void setEditTextPosition(final int index, RectF rectF) {
        //0.防止文字被串改
        if (editTextWatcher != null)
            fillBlankEditText.removeTextChangedListener(editTextWatcher);
        //1.设置编辑框的位置
        RelativeLayout.LayoutParams layoutParams = (LayoutParams) fillBlankEditText.getLayoutParams();
        layoutParams.width = (int) (rectF.right - rectF.left);
        layoutParams.height = (int) (rectF.bottom - rectF.top);
        layoutParams.leftMargin = (int) (displayTextView.getLeft() + rectF.left);
        layoutParams.topMargin = (int) (displayTextView.getTop() + rectF.top);
        fillBlankEditText.setLayoutParams(layoutParams);
        //2.设置当前显示
        fillBlankEditText.setText(fillContents.get(index));
        fillBlankEditText.setVisibility(VISIBLE);
        //3.获取焦点,弹出软键盘
        fillBlankEditText.setFocusable(true);
        fillBlankEditText.requestFocus();
        fillBlankEditText.setSelection(fillBlankEditText.getText().toString().length());
        showImm(fillBlankEditText);
        //4.spans聚焦 不被聚焦的将被设置文字和取消焦点下划线
        for (int i = 0; i < fillContentsSpans.size(); i++) {
            if (i == index) {
                fillContentsSpans.get(i).setFocusable(true);
                fillContentsSpans.get(i).setNeedPaintText("");
            } else {
                fillContentsSpans.get(i).setFocusable(false);
                fillContentsSpans.get(i).setNeedPaintText(fillContents.get(i) != null ? fillContents.get(i) : "");
            }
        }
        displayTextView.postInvalidate();
        //5.监听文字变化
        editTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                fillContents.put(index, s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        };
        fillBlankEditText.addTextChangedListener(editTextWatcher);
        //6.监听失去焦点的变化
        fillBlankEditText.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    //将当前的内容填写在题目上
                    fillContents.put(index, fillBlankEditText.getText().toString());
                    fillContentsSpans.get(index).setFocusable(false);
                    fillContentsSpans.get(index).setNeedPaintText(fillContents.get(index));
                    displayTextView.postInvalidate();
                    close(v);
                    v.setVisibility(GONE);
                }
            }
        });
    }

    private TextWatcher editTextWatcher;

    /**
     * 打开软键盘
     */
    private void showImm(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.showSoftInput(view, 0);
    }

    private void close(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    /**
     * NULL转" "
     *
     * @param words 对象
     * @return " "or words
     */
    private String null2EmptyStr(String words) {
        if (words == null) return " ";
        return words;
    }
}