package com.remindme.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.Random;

/**
 * Native-drawn brand mark: recessed block letters on a softly textured felt box.
 * Keeping it code-drawn means the mark remains sharp at every screen density.
 */
public final class FeltLogoView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Random fibers = new Random(2407L);
    private final float density;

    public FeltLogoView(Context context) {
        this(context, null);
    }

    public FeltLogoView(Context context, AttributeSet attributes) {
        this(context, attributes, 0);
    }

    public FeltLogoView(Context context, AttributeSet attributes, int style) {
        super(context, attributes, style);
        density = getResources().getDisplayMetrics().density;
        textPaint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        float inset = dp(1);
        float radius = Math.min(width, height) * 0.28f;
        RectF box = new RectF(inset, inset, width - inset, height - inset);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(72, 91, 81));
        paint.setShadowLayer(dp(4), 0, dp(2), 0x2D1E2923);
        canvas.drawRoundRect(box, radius, radius, paint);
        paint.clearShadowLayer();

        Path clippingPath = new Path();
        clippingPath.addRoundRect(box, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clippingPath);
        fibers.setSeed(2407L + getWidth() * 31L + getHeight());
        for (int index = 0; index < 190; index++) {
            float x = fibers.nextFloat() * width;
            float y = fibers.nextFloat() * height;
            float length = dp(1.5f) + fibers.nextFloat() * dp(7);
            paint.setStrokeWidth(Math.max(1f, dp(.35f)));
            paint.setColor(fibers.nextBoolean() ? 0x286C8377 : 0x26405249);
            canvas.drawLine(x, y, x + length, y + (fibers.nextFloat() - .5f) * dp(1.3f), paint);
        }
        // A few long, very subtle threads sell the soft fabric feel without reducing legibility.
        paint.setStrokeWidth(Math.max(1f, dp(.45f)));
        paint.setColor(0x18405249);
        for (int index = 0; index < 12; index++) {
            float y = dp(5) + fibers.nextFloat() * (height - dp(10));
            canvas.drawLine(-dp(4), y, width + dp(4), y + dp(.5f), paint);
        }
        canvas.restore();

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(Math.min(width * .51f, height * .48f));
        float baseline = height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f;

        // A cool upper edge and dark lower edge make the letters look pressed into the felt.
        textPaint.setColor(0x657F978A);
        canvas.drawText("RE", width / 2f - dp(.7f), baseline - dp(.8f), textPaint);
        textPaint.setColor(0x9E263C31);
        canvas.drawText("RE", width / 2f + dp(1.1f), baseline + dp(1.25f), textPaint);
        textPaint.setColor(Color.rgb(65, 83, 73));
        canvas.drawText("RE", width / 2f, baseline, textPaint);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
