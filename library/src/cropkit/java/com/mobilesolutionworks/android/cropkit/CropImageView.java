package com.mobilesolutionworks.android.cropkit;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.media.FaceDetector;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.mobilesolutionworks.android.cropimage.R;
import com.mobilesolutionworks.android.cropimage.camera.CropImage;

import java.util.ArrayList;

import bolts.TaskCompletionSource;

/**
 * Created by yunarta on 7/7/14.
 */
public class CropImageView extends ImageViewTouchBase
{
    ArrayList<HighlightView> mHighlightViews      = new ArrayList<HighlightView>();
    HighlightView            mMotionHighlightView = null;
    float mLastX, mLastY;
    int mMotionEdge;

    final LayerDrawable mHighlight;

    Bitmap mImageBitmapResetBase;

    private boolean mSaving;

    @Override
    protected void onLayout(boolean changed, int left, int top,
                            int right, int bottom)
    {
        super.onLayout(changed, left, top, right, bottom);
        if (mBitmapDisplayed.getBitmap() != null)
        {
            for (HighlightView hv : mHighlightViews)
            {
                hv.mMatrix.set(getImageMatrix());
                hv.invalidate();
                if (hv.mIsFocused)
                {
                    centerBasedOnHighlightView(hv);
                }
            }
        }
    }

    public CropImageView(Context context, AttributeSet attrs)
    {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CropImageView, R.attr.cropKitStyle, R.style.CropKit);

        Drawable dHighlight = a.getDrawable(R.styleable.CropImageView_cropKitHighlight);
        if (!(dHighlight instanceof LayerDrawable)) throw new IllegalStateException("cropKitHightlight must be a layer-list");

        mHighlight = (LayerDrawable) dHighlight;
        int[] idCheck = {R.id.cropkit_highlight_diagonal, R.id.cropkit_highlight_horizontal, R.id.cropkit_highlight_vertical};

        for (int id : idCheck)
        {
            if (mHighlight.findDrawableByLayerId(id) == null)
            {
                throw new IllegalStateException("@id/" + getResources().getResourceEntryName(id) + " is not included in cropKitHightlight layer-list");
            }
        }

        a.recycle();
    }

    private TaskCompletionSource<Void> mFaceDetectionTask;

    @Override
    protected void onAttachedToWindow()
    {
        super.onAttachedToWindow();

        if (mFaceDetectionTask == null) {
            mFaceDetectionTask = new TaskCompletionSource<>();
            if (getScale() == 1F) {
                center(true, true);
                mRunFaceDetection.run();
            }
        }

    }

    @Override
    protected void zoomTo(float scale, float centerX, float centerY)
    {
        super.zoomTo(scale, centerX, centerY);
        for (HighlightView hv : mHighlightViews)
        {
            hv.mMatrix.set(getImageMatrix());
            hv.invalidate();
        }
    }

    @Override
    protected void zoomIn()
    {
        super.zoomIn();
        for (HighlightView hv : mHighlightViews)
        {
            hv.mMatrix.set(getImageMatrix());
            hv.invalidate();
        }
    }

    @Override
    protected void zoomOut()
    {
        super.zoomOut();
        for (HighlightView hv : mHighlightViews)
        {
            hv.mMatrix.set(getImageMatrix());
            hv.invalidate();
        }
    }

    @Override
    protected void postTranslate(float deltaX, float deltaY)
    {
        super.postTranslate(deltaX, deltaY);
        for (int i = 0; i < mHighlightViews.size(); i++)
        {
            HighlightView hv = mHighlightViews.get(i);
            hv.mMatrix.postTranslate(deltaX, deltaY);
            hv.invalidate();
        }
    }

    // According to the event's position, change the focus to the first
    // hitting cropping rectangle.
    private void recomputeFocus(MotionEvent event)
    {
        for (int i = 0; i < mHighlightViews.size(); i++)
        {
            HighlightView hv = mHighlightViews.get(i);
            hv.setFocus(false);
            hv.invalidate();
        }

        for (int i = 0; i < mHighlightViews.size(); i++)
        {
            HighlightView hv   = mHighlightViews.get(i);
            int           edge = hv.getHit(event.getX(), event.getY());
            if (edge != HighlightView.GROW_NONE)
            {
                if (!hv.hasFocus())
                {
                    hv.setFocus(true);
                    hv.invalidate();
                }
                break;
            }
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        CropImage cropImage = (CropImage) this.getContext();
        if (mSaving)
        {
            return false;
        }

        switch (event.getAction())
        {
            case MotionEvent.ACTION_DOWN:
                if (mWaitingToPick)
                {
                    recomputeFocus(event);
                }
                else
                {
                    for (int i = 0; i < mHighlightViews.size(); i++)
                    {
                        HighlightView hv   = mHighlightViews.get(i);
                        int           edge = hv.getHit(event.getX(), event.getY());
                        if (edge != HighlightView.GROW_NONE)
                        {
                            mMotionEdge = edge;
                            mMotionHighlightView = hv;
                            mLastX = event.getX();
                            mLastY = event.getY();
                            mMotionHighlightView.setMode(
                                    (edge == HighlightView.MOVE)
                                            ? HighlightView.ModifyMode.Move
                                            : HighlightView.ModifyMode.Grow);
                            break;
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mWaitingToPick)
                {
                    for (int i = 0; i < mHighlightViews.size(); i++)
                    {
                        HighlightView hv = mHighlightViews.get(i);
                        if (hv.hasFocus())
                        {
                            mCrop = hv;
                            for (int j = 0; j < mHighlightViews.size(); j++)
                            {
                                if (j == i)
                                {
                                    continue;
                                }
                                mHighlightViews.get(j).setHidden(true);
                            }
                            centerBasedOnHighlightView(hv);
                            mWaitingToPick = false;
                            return true;
                        }
                    }
                }
                else if (mMotionHighlightView != null)
                {
                    centerBasedOnHighlightView(mMotionHighlightView);
                    mMotionHighlightView.setMode(
                            HighlightView.ModifyMode.None);
                }
                mMotionHighlightView = null;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mWaitingToPick)
                {
                    recomputeFocus(event);
                }
                else if (mMotionHighlightView != null)
                {
                    mMotionHighlightView.handleMotion(mMotionEdge,
                            event.getX() - mLastX,
                            event.getY() - mLastY);
                    mLastX = event.getX();
                    mLastY = event.getY();

                    if (true)
                    {
                        // This section of code is optional. It has some user
                        // benefit in that moving the crop rectangle against
                        // the edge of the screen causes scrolling but it means
                        // that the crop rectangle is no longer fixed under
                        // the user's finger.
                        ensureVisible(mMotionHighlightView);
                    }
                }
                break;
        }

        switch (event.getAction())
        {
            case MotionEvent.ACTION_UP:
                center(true, true);
                break;
            case MotionEvent.ACTION_MOVE:
                // if we're not zoomed then there's no point in even allowing
                // the user to move the image around.  This call to center puts
                // it back to the normalized location (with false meaning don't
                // animate).
                if (getScale() == 1F)
                {
                    center(true, true);
                }
                break;
        }

        return true;
    }

    // Pan the displayed image to make sure the cropping rectangle is visible.
    private void ensureVisible(HighlightView hv)
    {
        Rect r = hv.mDrawRect;

        int panDeltaX1 = Math.max(0, this.getLeft() - r.left);
        int panDeltaX2 = Math.min(0, this.getRight() - r.right);

        int panDeltaY1 = Math.max(0, this.getTop() - r.top);
        int panDeltaY2 = Math.min(0, this.getBottom() - r.bottom);

        int panDeltaX = panDeltaX1 != 0 ? panDeltaX1 : panDeltaX2;
        int panDeltaY = panDeltaY1 != 0 ? panDeltaY1 : panDeltaY2;

        if (panDeltaX != 0 || panDeltaY != 0)
        {
            panBy(panDeltaX, panDeltaY);
        }
    }

    // If the cropping rectangle's size changed significantly, change the
    // view's center and scale according to the cropping rectangle.
    private void centerBasedOnHighlightView(HighlightView hv)
    {
        Rect drawRect = hv.mDrawRect;

        float width  = drawRect.width();
        float height = drawRect.height();

        float thisWidth  = getWidth();
        float thisHeight = getHeight();

        float z1 = thisWidth / width * .6F;
        float z2 = thisHeight / height * .6F;

        float zoom = Math.min(z1, z2);
        zoom = zoom * this.getScale();
        zoom = Math.max(1F, zoom);

        if ((Math.abs(zoom - getScale()) / zoom) > .1)
        {
            float[] coordinates = new float[]{hv.mCropRect.centerX(),
                    hv.mCropRect.centerY()};
            getImageMatrix().mapPoints(coordinates);
            zoomTo(zoom, coordinates[0], coordinates[1], 300F);
        }

        ensureVisible(hv);
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);
        for (int i = 0; i < mHighlightViews.size(); i++)
        {
            mHighlightViews.get(i).draw(canvas);
        }
    }

    public void add(HighlightView hv)
    {
        mHighlightViews.add(hv);
        invalidate();
    }

    @Override
    public void setImageBitmapResetBase(Bitmap bitmap, boolean resetSupp)
    {
        super.setImageBitmapResetBase(bitmap, resetSupp);
        mImageBitmapResetBase = bitmap;
    }

    private HighlightView mCrop;

    private boolean mDoFaceDetection;

    private boolean mCircleCrop;

    private int mAspectX;

    private int mAspectY;

    private boolean mWaitingToPick;

    Runnable mRunFaceDetection = new Runnable()
    {

        @SuppressWarnings("hiding")
        float mScale = 1F;
        Matrix mImageMatrix;
        FaceDetector.Face[] mFaces = new FaceDetector.Face[3];
        int mNumFaces;

        CropImageView mImageView;

        // For each face, we create a HightlightView for it.
        private void handleFace(FaceDetector.Face f)
        {
            PointF midPoint = new PointF();

            int r = ((int) (f.eyesDistance() * mScale)) * 2;
            f.getMidPoint(midPoint);
            midPoint.x *= mScale;
            midPoint.y *= mScale;

            int midX = (int) midPoint.x;
            int midY = (int) midPoint.y;

            HighlightView hv = new HighlightView(mImageView, mHighlight);

            int width  = mImageBitmapResetBase.getWidth();
            int height = mImageBitmapResetBase.getHeight();

            Rect imageRect = new Rect(0, 0, width, height);

            RectF faceRect = new RectF(midX, midY, midX, midY);
            faceRect.inset(-r, -r);
            if (faceRect.left < 0)
            {
                faceRect.inset(-faceRect.left, -faceRect.left);
            }

            if (faceRect.top < 0)
            {
                faceRect.inset(-faceRect.top, -faceRect.top);
            }

            if (faceRect.right > imageRect.right)
            {
                faceRect.inset(faceRect.right - imageRect.right,
                        faceRect.right - imageRect.right);
            }

            if (faceRect.bottom > imageRect.bottom)
            {
                faceRect.inset(faceRect.bottom - imageRect.bottom,
                        faceRect.bottom - imageRect.bottom);
            }

            hv.setup(mImageMatrix, imageRect, faceRect, mCircleCrop, mAspectX != 0 && mAspectY != 0);
            mImageView.add(hv);
        }

        // Create a default HightlightView if we found no face in the picture.
        private void makeDefault()
        {
            HighlightView hv = new HighlightView(mImageView, mHighlight);

            int width  = mImageBitmapResetBase.getWidth();
            int height = mImageBitmapResetBase.getHeight();

            Rect imageRect = new Rect(0, 0, width, height);

            // make the default size about 4/5 of the width or height
            int cropWidth  = Math.min(width, height) * 4 / 5;
            int cropHeight = cropWidth;

            if (mAspectX != 0 && mAspectY != 0)
            {
                if (mAspectX > mAspectY)
                {
                    cropHeight = cropWidth * mAspectY / mAspectX;
                }
                else
                {
                    cropWidth = cropHeight * mAspectX / mAspectY;
                }
            }

            int x = (width - cropWidth) / 2;
            int y = (height - cropHeight) / 2;

            RectF cropRect = new RectF(x, y, x + cropWidth, y + cropHeight);
            hv.setup(mImageMatrix, imageRect, cropRect, mCircleCrop, mAspectX != 0 && mAspectY != 0);
            mImageView.add(hv);
        }

        // Scale the image down for faster face detection.
        private Bitmap prepareBitmap()
        {
            if (mImageBitmapResetBase == null || mImageBitmapResetBase.isRecycled())
            {
                return null;
            }

            // 256 pixels wide is enough.
            if (mImageBitmapResetBase.getWidth() > 256)
            {
                mScale = 256.0F / mImageBitmapResetBase.getWidth();
            }
            Matrix matrix = new Matrix();
            matrix.setScale(mScale, mScale);
            Bitmap faceBitmap = Bitmap.createBitmap(mImageBitmapResetBase, 0, 0, mImageBitmapResetBase.getWidth(), mImageBitmapResetBase.getHeight(), matrix, true);
            return faceBitmap;
        }

        public void run()
        {
            mImageMatrix = mImageView.getImageMatrix();
            Bitmap faceBitmap = prepareBitmap();

            mScale = 1.0F / mScale;
            if (faceBitmap != null && mDoFaceDetection)
            {
                FaceDetector detector = new FaceDetector(faceBitmap.getWidth(),
                        faceBitmap.getHeight(), mFaces.length);
                mNumFaces = detector.findFaces(faceBitmap, mFaces);
            }

            if (faceBitmap != null && faceBitmap != mImageBitmapResetBase)
            {
                faceBitmap.recycle();
            }

            mHandler.post(new Runnable()
            {
                public void run()
                {
                    mWaitingToPick = mNumFaces > 1;
                    if (mNumFaces > 0)
                    {
                        for (int i = 0; i < mNumFaces; i++)
                        {
                            handleFace(mFaces[i]);
                        }
                    }
                    else
                    {
                        makeDefault();
                    }
                    mImageView.invalidate();
                    if (mImageView.mHighlightViews.size() == 1)
                    {
                        mCrop = mImageView.mHighlightViews.get(0);
                        mCrop.setFocus(true);
                    }

                    if (mNumFaces > 1)
                    {
//                        Toast t = Toast.makeText(CropImage.this, metaData.getInt("imagecrop_multiface_crop_help", R.string.z_imagecrop_multiface_crop_help), Toast.LENGTH_SHORT);
//                        t.show();
                    }

                    mFaceDetectionTask.trySetResult(null);
                }
            });
        }
    };

    public void clearHighlights()
    {
        mHighlightViews.clear();
    }
}