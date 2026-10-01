package com.haru.haru_stay.ui.fragment.testfragment

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.haru.haru_stay.R
import com.google.android.material.bottomsheet.BottomSheetBehavior

/**
 * BottomSheet의 드래그 시작 영역을
 * bottom_sheet_drag_area로 제한하는 Behavior.
 *
 * - 손잡이 + 날짜 영역에서 시작
 *      → BottomSheet 드래그
 *
 * - RecyclerView에서 시작
 *      → RecyclerView만 스크롤
 *
 * BottomSheet의 30% / 50% / 60dp 위치 설정은
 * 기존 BottomSheetBehavior 설정을 그대로 사용한다.
 */
class DragAreaBottomSheetBehavior<V : View> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BottomSheetBehavior<V>(context, attrs) {

    /**
     * 현재 터치가 BottomSheet 드래그 영역에서 시작했는지 여부.
     */
    private var allowDrag = false

    override fun onInterceptTouchEvent(
        parent: CoordinatorLayout,
        child: V,
        event: MotionEvent
    ): Boolean {

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {

                val dragArea =
                    child.findViewById<View>(
                        R.id.bottom_sheet_drag_area
                    )

                if (dragArea != null) {

                    val location =
                        IntArray(2)

                    dragArea.getLocationOnScreen(location)

                    val x = event.rawX
                    val y = event.rawY

                    allowDrag =
                        x >= location[0] &&
                                x <= location[0] + dragArea.width &&
                                y >= location[1] &&
                                y <= location[1] + dragArea.height
                } else {
                    allowDrag = false
                }
            }
        }

        /*
         * BottomSheet 드래그 영역에서 시작한 터치만
         * 기존 BottomSheetBehavior에게 전달한다.
         */
        if (allowDrag) {

            val result =
                super.onInterceptTouchEvent(
                    parent,
                    child,
                    event
                )

            if (
                event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                allowDrag = false
            }

            return result
        }

        /*
         * RecyclerView 등 다른 영역에서 시작한 터치는
         * BottomSheet가 직접 가로채지 않는다.
         */
        if (
            event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            allowDrag = false
        }

        return false
    }

    /**
     * RecyclerView의 nested scroll을
     * BottomSheet 이동에 사용하지 않는다.
     *
     * 따라서 RecyclerView 영역에서는
     * BottomSheet가 따라 움직이지 않고
     * RecyclerView 자체만 스크롤한다.
     */
    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {

        return false
    }
}