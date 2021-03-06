package com.jianghongkui.lib_rules.detector

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.JavaContext
import com.jianghongkui.lib_rules.UsageIssue
import com.jianghongkui.lib_rules.base.BaseDetector
import com.jianghongkui.lib_rules.base.BaseUastVisitor
import org.jetbrains.uast.*
import java.util.*

/**
 * 处理与条件语句使用相关规则
 *
 * @author jianghongkui
 * @date 2018/10/25
 */
open class IfUsageDetector : BaseDetector.JavaDetector() {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        val list = ArrayList<Class<out UElement>>()
        list.add(UMethod::class.java)
        return list
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {

            override fun visitMethod(node: UMethod) {
                val visitor = IfVisitor()
                if (node.uastBody != null)
                    visitor.start(context, node.uastBody)
            }
        }
    }

    class IfVisitor : BaseUastVisitor<JavaContext>() {
        private var isNeedCheck = true
        private var lockCode = -1

        private var reportNode: UIfExpression? = null

        private val ifExpressions = Stack<UIfExpression>()

        override fun visitIfExpression(node: UIfExpression): Boolean {
            if (!isNeedCheck) return super.visitIfExpression(node)
            if (node.isTernary) return super.visitIfExpression(node)
            if (ifExpressions.isEmpty()) {
                ifExpressions.push(node)
                return super.visitIfExpression(node)
            }
            if (reportNode != null) {
                if (node.isChildOf(reportNode, true)) {
                    return super.visitIfExpression(node)
                }
                ifExpressions.empty()
                ifExpressions.push(node)
                reportNode = null
                return super.visitIfExpression(node)
            }
            while (ifExpressions.isNotEmpty()) {
                val expression = ifExpressions.peek()
                if (node.isChildOf(expression, false)) {
                    if (expression.elseIdentifier != null &&
                            context.getLocation(expression.elseIdentifier!!).end!!.line ==
                            context.getLocation(node.ifIdentifier).start!!.line) {
                        // else if 类型
                    } else {
                        ifExpressions.push(node)
                        if (ifExpressions.size > 3) {
                            val msg = "if嵌套层数不能大于3"
                            reportNode = ifExpressions.first()
                            context.report(UsageIssue.ISSUE_IF, reportNode, context.getLocation(reportNode!!), msg)
                        }
                        return super.visitIfExpression(node)
                    }
                }
                ifExpressions.pop()
            }
            ifExpressions.push(node)
            return super.visitIfExpression(node)
        }

        override fun afterVisitIfExpression(node: UIfExpression) {
            super.afterVisitIfExpression(node)
            if (reportNode != null && node == reportNode) {
                reportNode = null
            }

        }

        override fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean {
            lock(node.hashCode())
            return super.visitObjectLiteralExpression(node)
        }

        override fun afterVisitObjectLiteralExpression(node: UObjectLiteralExpression) {
            unLock(node.hashCode())
        }

        private fun lock(hashCode: Int) {
            if (lockCode > 0) return
            isNeedCheck = false
            lockCode = hashCode
        }

        private fun unLock(hashCode: Int) {
            if (lockCode == hashCode) {
                isNeedCheck = true
                lockCode = -1
            }
        }
    }
}