package jp.ponko2.android.widget

import _root_.android.widget.TextView
import _root_.android.content.Context
import _root_.android.util.AttributeSet
import _root_.android.text.TextUtils
import _root_.android.graphics.Rect

class MarqueeTextView(context: Context, attrs: AttributeSet, defStyle: Int)
  extends TextView(context, attrs, defStyle) {
  super.setEllipsize(TextUtils.TruncateAt.MARQUEE)

  def this(context: Context) {
    this(context, null, android.R.attr.textViewStyle)
  }

  def this(context: Context, attrs: AttributeSet) {
    this(context, attrs, android.R.attr.textViewStyle)
  }

  override protected def onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect) {
    if(focused) super.onFocusChanged(focused, direction, previouslyFocusedRect)
  }

  override protected def onWindowFocusChanged(focused: Boolean) {
    if(focused) super.onWindowFocusChanged(focused)
  }

  override def setEllipsize(where: TextUtils.TruncateAt) {
    super.setEllipsize(TextUtils.TruncateAt.MARQUEE)
  }

  override def isFocused() = true
}
