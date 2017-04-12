package com.emp.cammo;

import android.app.Fragment;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;


public class FragmentTracking extends Fragment implements CameraBridgeViewBase.CvCameraViewListener2 {

    // member variables
    public int mCameraIndex = Camera.CameraInfo.CAMERA_FACING_BACK;
    private Mat mMatRgba; // color image of current frame given by CameraView
//    private Mat mMatGray; // gray image of current frame given by CameraView
    private MatOfPoint2f mCorners;
    private final static int mFindFlags = Calib3d.CALIB_CB_FAST_CHECK;

    // LAZY SHAME
    private final static Size mBoardSize = new Size(4, 3);
    final float mSquareSize = 30;
    private MatOfPoint3f mObjectPoints = null;
    private Mat rvec = null;
    private Mat tvec = null;

    // EVEN LAZIER
    MatOfPoint3f worldPoints = null;
    MatOfPoint2f imagePoints = null;
//    MatOfPoint3f axis3 = null;
//    MatOfPoint2f axis2 = null;
    Mat mCamera = null;
    MatOfDouble mDistortion = null;


    // widgets
    private CameraView mCameraView = null;

    // constructor
    public static FragmentTracking newInstance() {
        return new FragmentTracking();
    }

    // fragment lifecycle
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get parent activity (MainActivity)
        MainActivity parent = (MainActivity) getActivity();
        if (null != parent) {
            // keep window on
            parent.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tracking, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // setup widgets
        mCameraView = (CameraView) view.findViewById(R.id.cameraView);
    }

    @Override
    public void onResume() {
        super.onResume();

        // setup mCamera view widget
        if (null != mCameraView) {
            mCameraView.setCvCameraViewListener(this);  // set listener for mCamera view callbacks
            mCameraView.setCameraIndex(mCameraIndex);
            mCameraView.enableView();                   // turn mCamera stream on
            mCameraView.setVisibility(View.VISIBLE);    // set widget visibility on
        }

        // done
        setRetainInstance(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (null != mCameraView) {
            mCameraView.disableView();
            mCameraView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (null != mCameraView) {
            mCameraView.disableView();
            mCameraView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    // mCamera view callbacks
    @Override
    public void onCameraViewStarted(int width, int height) { // size of stream or preview?
        // setup image buffer just once before streaming
        mMatRgba = new Mat(height, width, CvType.CV_8UC4);
//        mMatGray = new Mat(height, width, CvType.CV_8UC1);
        rvec = new Mat();
        tvec = new Mat();
        mCorners = new MatOfPoint2f();
        calcObjectPoints(); // mObjectPoints

        imagePoints = new MatOfPoint2f();
        worldPoints = new MatOfPoint3f(
                new Point3(0, 0, -1),
                new Point3(0, 1, -1),
                new Point3(1, 1, -1),
                new Point3(1, 0, -1));
        worldPoints.mul(worldPoints, mSquareSize);

//        axis2 = new MatOfPoint2f();
//        axis3 = new MatOfPoint3f(
//                new Point3(mSquareSize, 0, 0),
//                new Point3(0, mSquareSize, 0),
//                new Point3(0, 0, -mSquareSize));

        MainActivity parent = (MainActivity) getActivity();
        mCamera = parent.mCameraParameters.getCameraMatrix();
        mDistortion = new MatOfDouble(parent.mCameraParameters.getDistortion());
    }

    @Override
    public void onCameraViewStopped() {
        // 'free' image buffer after streaming
        mMatRgba.release();
//        mMatGray.release();
        mCorners.release();
        rvec.release();
        tvec.release();
        mObjectPoints.release();

        worldPoints.release();
        imagePoints.release();
//        axis3.release();
//        axis2.release();
        mDistortion.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame frame) {
        // get color frame from mCamera
        mMatRgba = frame.rgba();

        // front mCamera is flipped, fix that here if it's active
        if (Camera.CameraInfo.CAMERA_FACING_FRONT == mCameraIndex) {
            Core.flip(mMatRgba, mMatRgba, Core.ROTATE_180);
        }

        // find checkerboard
        boolean found = Calib3d.findChessboardCorners(frame.gray(), mBoardSize, mCorners, mFindFlags);
        if (found) {
            // draw chessboard corners
//            Calib3d.drawChessboardCorners(mMatRgba, mBoardSize, mCorners, true /*found*/);

            // find rotation and translation vectors
            Calib3d.solvePnP(mObjectPoints, mCorners, mCamera, mDistortion, rvec, tvec);

            // project 3d point onto 2d
            Calib3d.projectPoints(worldPoints, rvec, tvec, mCamera, mDistortion, imagePoints);

            // draw axis
            Point corner = new Point(mCorners.get(0, 0));
            List<MatOfPoint> pointList = new ArrayList<>(1);
            pointList.add(new MatOfPoint(imagePoints));

            Imgproc.drawContours(mMatRgba, pointList, -1, new Scalar(255), 5);

//            Imgproc.drawContours(mMatRgba, new List<MatOfPoint>(imgPoints), -1, new Scalar(255));
//            Imgproc.line(mMatRgba, corner, imgPoints[0], new Scalar(255, 0, 0), 2);
//            Imgproc.line(mMatRgba, corner, imgPoints[1], new Scalar(0, 255, 0), 2);
//            Imgproc.line(mMatRgba, corner, imgPoints[2], new Scalar(0, 0, 255), 2);

//            // draw background
//            Mat roi = new Mat(mMatRgba, new Rect(0, 0, 160, 100));
//            Mat infoBlock = new Mat(roi.size(), mMatRgba.type(), new Scalar(255, 255, 255));
//            Core.addWeighted(infoBlock, 0.3, roi, 0.7, 0., roi);
//
//            // draw x
//            final String x = String.format(Locale.US, "x = %f", imgPoints[0].x);
//            Imgproc.putText(mMatRgba, x, new Point(10, 25), Core.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0));
//
//            // draw y
//            final String y = String.format(Locale.US, "y = %f", imgPoints[0].y);
//            Imgproc.putText(mMatRgba, y, new Point(10, 50), Core.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0));

//            // draw z
//            final String z = String.format(Locale.US, "z = %f", tvec.get(2, 0)[0]);
//            Imgproc.putText(mMatRgba, z, new Point(10, 75), Core.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0));
        }

        // return the image we want to preview
        return mMatRgba;
    }

    private void calcObjectPoints() {
        final int nPoints = (int)(mBoardSize.width * mBoardSize.height);
        float positions[] = new float[nPoints * 3];
        int i = 0;
        for (int row = 0; row < mBoardSize.height; row++) {
            for (int col = 0; col < mBoardSize.width; col++) {
                positions[i++] = col * mSquareSize;
                positions[i++] = row * mSquareSize;
                positions[i++] = 0.f;
            }
        }

        mObjectPoints = new MatOfPoint3f();
        mObjectPoints.create(nPoints, 1, CvType.CV_32FC3);
        mObjectPoints.put(0, 0, positions);
    }

    private String MatToString(Mat mat) {
        if (null == mat) return "null";

        final int nChannels = mat.channels();

        String s = "";
        final int nRows = mat.rows();
        final int nCols = mat.cols();
        for (int row = 0; row < nRows; row++) {
            s += "\n\t";
            for (int col = 0; col < nCols; col++) {
                for (int chan = 0; chan < nChannels; chan++) {
                    s += mat.get(row, col)[chan] + ", ";
                }
            }
        }

        return s;
    }


}
