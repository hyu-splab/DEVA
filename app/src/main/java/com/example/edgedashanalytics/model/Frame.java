package com.example.edgedashanalytics.model;

import android.os.Parcel;
import android.util.Size;

import androidx.annotation.NonNull;

public class Frame extends Content {

    // [Frame]
    private final int frameNumber;
    private final Size size;

    public static final Creator<Frame> CREATOR = new Creator<>() {
        @Override
        public Frame createFromParcel(Parcel parcel) {
            return new Frame(parcel);
        }

        @Override
        public Frame[] newArray(int i) {
            return new Frame[i];
        }

    };

    private Frame(Parcel in) {
        super(in.readString(), in.readString());
        this.frameNumber = 0;
        this.size = new Size(0, 0);
    }

    public Frame(String data, String name, int frameNumber, Size size) {
        super(data, name);
        this.frameNumber = frameNumber;
        this.size = size;
    }

    public Frame(Frame frame) {
        super(frame.data, frame.name);
        this.frameNumber = frame.frameNumber;
        this.size = frame.size;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(data);
        parcel.writeString(name);
        parcel.writeInt(size.getWidth());
        parcel.writeInt(size.getHeight());
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
