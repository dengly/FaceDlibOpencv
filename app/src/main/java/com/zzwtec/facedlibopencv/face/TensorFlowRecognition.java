package com.zzwtec.facedlibopencv.face;

import android.graphics.RectF;

public class TensorFlowRecognition {
    /**
     * A unique identifier for what has been recognized. Specific to the class, not the instance of the object.
     * 已被识别的唯一标识符。 具体到类，而不是对象的实例。
     */
    private final String id;

    /**
     * Display name for the recognition.
     * 显示识别的名称。
     */
    private final String title;

    /**
     * A sortable score for how good the recognition is relative to others. Higher should be better.
     * 对于相对于其他人的认可程度有多高的分数。 越高越好。
     */
    private final Float confidence;

    /**
     * Optional location within the source image for the location of the recognized object.
     * 源图像中用于识别对象位置的可选位置。
     */
    private RectF location;

    public TensorFlowRecognition(
            final String id, final String title, final Float confidence, final RectF location) {
        this.id = id;
        this.title = title;
        this.confidence = confidence;
        this.location = location;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Float getConfidence() {
        return confidence;
    }

    public RectF getLocation() {
        return new RectF(location);
    }

    public void setLocation(RectF location) {
        this.location = location;
    }

    @Override
    public String toString() {
        String resultString = "";
        if (id != null) {
            resultString += "[" + id + "] ";
        }

        if (title != null) {
            resultString += title + " ";
        }

        if (confidence != null) {
            resultString += String.format("(%.1f%%) ", confidence * 100.0f);
        }

        if (location != null) {
            resultString += location + " ";
        }

        return resultString.trim();
    }
}
