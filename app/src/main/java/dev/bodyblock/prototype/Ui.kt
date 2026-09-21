package dev.bodyblock.prototype

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*

object Ui {
    val bg=Color.rgb(16,7,19); val card=Color.rgb(30,23,36); val pink=Color.rgb(255,0,140)
    val white=Color.rgb(248,242,250); val muted=Color.rgb(174,159,183); val line=Color.rgb(67,40,64)
    fun dp(c: Context,n: Int)=(c.resources.displayMetrics.density*n).toInt()
    fun shape(color: Int,stroke: Int=Color.TRANSPARENT,radius: Float=18f)=GradientDrawable().apply { setColor(color); cornerRadius=radius; setStroke(1,stroke) }
    fun column(c: Context)=LinearLayout(c).apply { orientation=LinearLayout.VERTICAL }
    fun text(c: Context,s: String,size: Float=15f,color: Int=white,bold: Boolean=false)=TextView(c).apply {
        text=s; textSize=size; setTextColor(color); if(bold) typeface=Typeface.create("sans-serif",Typeface.BOLD)
        setLineSpacing(dp(c,3).toFloat(),1f)
    }
    fun button(c: Context,label: String,primary: Boolean=false,action: ()->Unit)=Button(c).apply {
        text=label; isAllCaps=false; textSize=14f; setTextColor(white)
        backgroundTintList=ColorStateList.valueOf(if(primary) pink else Color.rgb(65,32,65))
        minimumHeight=dp(c,48); setOnClickListener { action() }
    }
    fun card(parent: LinearLayout,title: String?=null): LinearLayout {
        val c=parent.context
        return column(c).apply {
            setPadding(dp(c,18),dp(c,18),dp(c,18),dp(c,18)); background=shape(card,line,dp(c,20).toFloat())
            parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(c,14) })
            if(title!=null) addView(text(c,title,12f,muted,true).apply { letterSpacing=.12f; setPadding(0,0,0,dp(c,12)) })
        }
    }
    fun gap(parent: LinearLayout,n: Int=10) { parent.addView(Space(parent.context),LinearLayout.LayoutParams(1,dp(parent.context,n))) }
    fun toggle(parent: LinearLayout,label: String,value: Boolean,change: (Boolean)->Unit): Switch = Switch(parent.context).apply {
        text=label; textSize=14f; setTextColor(white); isChecked=value; minHeight=dp(context,48); setPadding(0,dp(context,4),0,dp(context,4))
        thumbTintList=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(pink,muted))
        setOnCheckedChangeListener { _, checked -> change(checked) }; parent.addView(this,LinearLayout.LayoutParams(-1,-2))
    }
    fun slider(parent: LinearLayout,label: String,value: Int,min: Int,max: Int,change: (Int)->Unit) {
        val title=text(parent.context,"$label  ·  $value",13f,muted); parent.addView(title)
        parent.addView(SeekBar(parent.context).apply {
            this.max=max-min; progress=value-min
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar,p: Int,user: Boolean) { title.text="$label  ·  ${p+min}"; if(user) change(p+min) }
                override fun onStartTrackingTouch(s: SeekBar) {} ; override fun onStopTrackingTouch(s: SeekBar) {}
            })
        },LinearLayout.LayoutParams(-1,dp(parent.context,42)))
    }
    fun select(parent: LinearLayout,label: String,values: List<String>,current: String,change: (String)->Unit) {
        parent.addView(text(parent.context,label,13f,muted))
        parent.addView(Spinner(parent.context).apply {
            adapter=ArrayAdapter(context,android.R.layout.simple_spinner_dropdown_item,values)
            setSelection(values.indexOf(current).coerceAtLeast(0))
            onItemSelectedListener=object: AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?,v: View?,position: Int,id: Long) { if(values[position]!=current) change(values[position]) }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        },LinearLayout.LayoutParams(-1,dp(parent.context,48)))
    }
    fun root(activity: Activity): LinearLayout = column(activity).apply {
        setBackgroundColor(bg)
        setOnApplyWindowInsetsListener { v,insets -> val s=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()); v.setPadding(s.left,s.top,s.right,s.bottom); insets }
        activity.setContentView(this)
    }
}
