package com.haru.haru_stay.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.haru.haru_stay.R

// 가상 데이터 클래스 (나중에 실제 DB 모델로 교체하면 됩니다)
// 🕒 시간, 상태, 와이파이 정보를 모두 받는 3총사 구조로 복구!
data class TimelineItem(
    val time: String,
    val adm: String,
    val wifi: String
)
class HomeAdapter(private val items: List<TimelineItem>) : RecyclerView.Adapter<HomeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // textViewLogTime 대신 방금 만든 커스텀 인디케이터 뷰 ID로 매핑 (필요시 참조)
        //val homeIndicatorView: View = view.findViewById(R.id.homeIndicatorView)
        val textViewAdm: TextView = view.findViewById(R.id.textViewLogAdm)
        val textViewWifi: TextView = view.findViewById(R.id.textViewLogWifi)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_home_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        // 시간은 HomeIndicatorView가 알아서 눈금과 정각을 그리므로 데이터에서 시간 바인딩 코드는 뺍니다.
        holder.textViewAdm.text = item.adm
        holder.textViewWifi.text = item.wifi
    }

    override fun getItemCount() = items.size
}