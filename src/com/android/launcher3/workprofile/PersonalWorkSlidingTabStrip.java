/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.workprofile;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.pageindicators.PageIndicator;
import com.android.launcher3.views.ActivityContext;

import app.lawnchair.font.FontManager;
import app.lawnchair.theme.color.tokens.ColorStateListTokens;
import app.lawnchair.theme.color.tokens.ColorTokens;

/**
 * Supports two indicator colors, dedicated for personal and work tabs.
 * Lawnchair/BeachChair custom Material 3 Segmented Control implementation.
 */
public class PersonalWorkSlidingTabStrip extends LinearLayout implements PageIndicator {
    private final boolean mIsAlignOnIcon;
    private OnActivePageChangedListener mOnActivePageChangedListener;
    private int mLastActivePage = 0;
    private float mScrollProgress = 0f;
    private final Paint mContainerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PersonalWorkSlidingTabStrip(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedArray typedArray = context.obtainStyledAttributes(attrs,
                R.styleable.PersonalWorkSlidingTabStrip);
        mIsAlignOnIcon = typedArray.getBoolean(
                R.styleable.PersonalWorkSlidingTabStrip_alignOnIcon, false);
        typedArray.recycle();

        setWillNotDraw(false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        FontManager fontManager = FontManager.INSTANCE.get(getContext());
        for (int i = 0; i < getChildCount(); i++) {
            Button tab = (Button) getChildAt(i);
            tab.setAllCaps(false);

            TypedValue outValue = new TypedValue();
            getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
            tab.setBackgroundResource(outValue.resourceId);

            tab.setTextColor(ColorStateListTokens.AllAppsTabText.resolve(getContext()));
            fontManager.setCustomFont(tab, R.id.font_body_medium);
        }
    }

    /**
     * Highlights tab with index pos
     */
    public void updateTabTextColor(int pos) {
        for (int i = 0; i < getChildCount(); i++) {
            Button tab = (Button) getChildAt(i);
            tab.setSelected(i == pos);
        }
    }

    @Override
    public void setScroll(int currentScroll, int totalScroll) {
        if (totalScroll > 0) {
            mScrollProgress = (float) currentScroll / totalScroll;
        } else {
            mScrollProgress = 0f;
        }
        if (mScrollProgress < 0f) mScrollProgress = 0f;
        if (mScrollProgress > 1f) mScrollProgress = 1f;

        // Dynamically select the tab based on scroll progress
        int activePage = mScrollProgress < 0.5f ? 0 : 1;
        updateTabTextColor(activePage);

        invalidate();
    }

    @Override
    public void setActiveMarker(int activePage) {
        updateTabTextColor(activePage);
        if (mOnActivePageChangedListener != null && mLastActivePage != activePage) {
            mOnActivePageChangedListener.onActivePageChanged(activePage);
        }
        mLastActivePage = activePage;
    }

    public void setOnActivePageChangedListener(OnActivePageChangedListener listener) {
        mOnActivePageChangedListener = listener;
    }

    @Override
    public void setMarkersCount(int numMarkers) {
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int containerColor = ColorTokens.SurfaceContainerHighest.resolveColor(getContext());
        int indicatorColor = ColorTokens.AllAppsTabBackgroundSelected.resolveColor(getContext());

        mContainerPaint.setColor(containerColor);
        mIndicatorPaint.setColor(indicatorColor);

        float width = getWidth();
        float height = getHeight();

        float containerLeft = getPaddingLeft();
        float containerTop = getPaddingTop();
        float containerRight = width - getPaddingRight();
        float containerBottom = height - getPaddingBottom();

        float rx = (containerBottom - containerTop) / 2f;
        float ry = rx;

        canvas.drawRoundRect(containerLeft, containerTop, containerRight, containerBottom, rx, ry, mContainerPaint);

        int count = getChildCount();
        if (count > 0) {
            float availableWidth = containerRight - containerLeft;
            float tabWidth = availableWidth / count;

            float left = containerLeft + mScrollProgress * (availableWidth - tabWidth);

            float indicatorLeft = left;
            float indicatorTop = containerTop;
            float indicatorRight = left + tabWidth;
            float indicatorBottom = containerBottom;

            float indicatorRx = (indicatorBottom - indicatorTop) / 2f;
            float indicatorRy = indicatorRx;

            canvas.drawRoundRect(indicatorLeft, indicatorTop, indicatorRight, indicatorBottom, indicatorRx, indicatorRy, mIndicatorPaint);
        }

        super.onDraw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = MeasureSpec.getSize(widthMeasureSpec);
        if (mIsAlignOnIcon) {
            size = getTabWidth(getContext(), size);
        }
        int maxTabWidth = (int) (320 * getResources().getDisplayMetrics().density);
        if (size > maxTabWidth) {
            size = maxTabWidth;
        }
        widthMeasureSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Returns distance between left and right app icons
     */
    public static int getTabWidth(Context context, int totalWidth) {
        DeviceProfile grid = ActivityContext.lookupContext(context).getDeviceProfile();
        int iconPadding = totalWidth / grid.numShownAllAppsColumns
                - grid.getAllAppsProfile().getIconSizePx();
        return totalWidth - iconPadding;
    }

    /**
     * Interface definition for a callback to be invoked when an active page has been changed.
     */
    public interface OnActivePageChangedListener {
        /** Called when the active page has been changed. */
        void onActivePageChanged(int currentActivePage);
    }
}
