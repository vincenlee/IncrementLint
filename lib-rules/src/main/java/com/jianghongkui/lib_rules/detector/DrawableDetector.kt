package com.jianghongkui.lib_rules.detector

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.XmlContext
import com.jianghongkui.lib_rules.AnalysisIssue
import com.jianghongkui.lib_rules.UsageIssue
import com.jianghongkui.lib_rules.base.BaseDetector
import com.jianghongkui.lib_rules.getUnitFromString
import org.w3c.dom.Attr
import org.w3c.dom.Element
import java.util.*
import kotlin.collections.HashMap

/**
 * 兼容性问题：
 * 1、虚线高度问题
 * 2、shape中需要添加solid
 *
 * @author jianghongkui
 * @date 2018/10/22
 */
open class DrawableDetector : BaseDetector.XmlDetector() {

    private var isLine: Boolean = false

    var data: HashMap<String, String> = HashMap(10)

    companion object {
        val NORMAL_LAYOUT_UNIT: List<String> = Arrays.asList("match_parent", "wrap_content", "fill_parent")
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
            ResourceFolderType.LAYOUT == folderType || ResourceFolderType.DRAWABLE == folderType

    override fun getApplicableElements(): Collection<String>? = Arrays.asList("shape", "stroke")

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "shape" -> {
                if (element.hasAttribute("android:shape") &&
                        element.getAttribute("android:shape") == "line") {
                    isLine = true
                }
                checkSoild(context, element)
            }
            "stroke" -> {
                if (isLine && element.hasAttribute("android:dashGap")&& element.hasAttribute("android:width")) {
                    val value = element.getAttribute("android:width")
                    if (value.startsWith("@")) {
                        return
                    }

                    data[context.file.name.replace(".xml", "")] = value
                }
            }
        }
    }

    override fun getApplicableAttributes(): Collection<String>? = Arrays.asList("background")

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        checkShapeLineUse(context, attribute)
    }

    override fun afterCheckFile(context: Context) {
        isLine = false
    }

    private fun checkSoild(context: XmlContext, element: Element) {
        if (!context.isEnabled(UsageIssue.ISSUE_SHAPE_BACKGROUND)) return
        val nodeList = element.getElementsByTagName("solid")
        if (nodeList.length == 0) {
            if (element.getElementsByTagName("gradient").length > 0) return
            val shapeLocation = context.getLocation(element)
            context.report(UsageIssue.ISSUE_SHAPE_BACKGROUND, shapeLocation,
                    UsageIssue.ISSUE_SHAPE_BACKGROUND.getBriefDescription(TextFormat.TEXT))
        } else {
            var i = 0
            do {
                val node = nodeList.item(i)
                if (node.attributes.length > 0) {
                    i++
                    continue
                }
                context.report(UsageIssue.ISSUE_SHAPE_BACKGROUND, context.getLocation(node), "solid中需添加背景色")
                i++
            } while (i < nodeList.length)
        }
    }

    private fun checkShapeLineUse(context: XmlContext, attribute: Attr) {
        if (!context.isEnabled(AnalysisIssue.ISSUE_SHAPE_LINE)) return
        if (!attribute.ownerElement.hasAttribute("android:layout_height")) return
        val height = attribute.ownerElement.getAttributeNode("android:layout_height")
        if (NORMAL_LAYOUT_UNIT.contains(height.value)) {
            return
        }
        if (attribute.value.startsWith("@drawable/")) {
            val valueIndex = attribute.value.substringAfter('/')
            for ((key, value) in data) {
                if (key == valueIndex) {
                    val unit = value.getUnitFromString()
                    val unit2 = height.value.getUnitFromString()
                    if (!unit.contentEquals(unit2)) {
                        if ((unit.contentEquals("dp") && unit2.contentEquals("dip")) ||
                                (unit.contentEquals("dip") && unit2.contentEquals("dp"))) {
                            //dip 和 dp 一样
                        } else {
                            return
                        }
                    }
                    if (value.substringBefore(unit).toFloat() > height.value.substringBefore(unit2).toFloat())
                        context.report(AnalysisIssue.ISSUE_SHAPE_LINE, attribute.ownerElement,
                                context.getLocation(attribute.ownerElement), "$key width$value" +
                                " android:layout_height的值不应小于$value")
                    return

                }
            }
        }
    }
}
