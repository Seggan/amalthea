package io.github.seggan.amalthea.backend.function

import io.github.seggan.amalthea.backend.OutputClass
import io.github.seggan.amalthea.backend.boxConditional
import io.github.seggan.amalthea.backend.struct.CompiledStruct
import io.github.seggan.amalthea.frontend.Intrinsics
import io.github.seggan.amalthea.frontend.QualifiedName
import io.github.seggan.amalthea.frontend.parsing.AstNode
import io.github.seggan.amalthea.frontend.typing.*
import io.github.seggan.amalthea.frontend.typing.function.LocalVariable
import io.github.seggan.amalthea.frontend.typing.function.Signature
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.*

class FunctionCompiler private constructor(
    private val signature: Signature,
    private val queryEngine: QueryEngine,
    val mv: DeferredMethodVisitor
) {

    private val usedFunctions = mutableSetOf<CompiledFunction>()
    private val usedStructs = mutableSetOf<CompiledStruct>()

    private val variables = mutableMapOf<LocalVariable, Pair<Int, Label>>()
    private var variableIndex = if (signature.name.name == "main") 1 else 0
    private var scope = 0

    private fun compile(ast: AstNode.Function<TypeData>) {
        addTypes(ast)

        for (param in (ast.extra as TypeData.Function).parameters) {
            variables[param] = variableIndex++ to Label()
        }

        compileBlock(ast.body)

        for ((variable, pair) in variables) {
            val (index, startLabel) = pair
            mv.visitLocalVariable(
                variable.name,
                variable.type.descriptor,
                null,
                startLabel,
                Label(),
                index
            )
        }

        val returnType = signature.type.returnType
        if (returnType.isJavaVoid) {
            mv.visitInsn(RETURN)
        } else {
            mv.visitInsn(returnType.asmType.getOpcode(IRETURN))
        }
    }

    private fun compileBlock(node: AstNode.Block<TypeData>) {
        scope++
        val lastIndex = variableIndex

        for (statement in node.statements) {
            compileStatement(statement)
        }

        val outOfScope = variables.filterValues { it.first == scope }
        for ((variable, pair) in outOfScope) {
            val (index, startLabel) = pair
            mv.visitLocalVariable(
                variable.name,
                variable.type.descriptor,
                null,
                startLabel,
                Label(),
                index
            )
            variables.remove(variable)
        }

        variableIndex = lastIndex
        scope--
    }

    private fun compileStatement(node: AstNode.Statement<TypeData>) {
        addTypes(node)
        when (node) {
            is AstNode.Expression -> compileExpression(node).also {
                if (!node.resolvedType.isJavaVoid) {
                    mv.visitInsn(POP)
                }
            }

            is AstNode.Block -> compileBlock(node)
            is AstNode.Return -> compileReturn(node)
            is AstNode.VariableAssignment -> compileVariableAssignment(node)
            is AstNode.VariableDeclaration -> compileVariableDeclaration(node)
            is AstNode.StructMutation -> compileStructMutation(node)
            is AstNode.If -> compileIf(node)
            is AstNode.While -> compileWhile(node)
        }
    }

    private fun compileVariableDeclaration(node: AstNode.VariableDeclaration<TypeData>) {
        val varData = node.extra as TypeData.Variable
        val index = variableIndex++
        variables[varData.variable] = index to Label()
        if (node.expr != null) {
            compileExpression(node.expr)
            mv.boxConditional(node.expr.resolvedType, varData.type)
            mv.visitVarInsn(varData.type.asmType.getOpcode(ISTORE), index)
        }
    }

    private fun compileVariableAssignment(node: AstNode.VariableAssignment<TypeData>) {
        val varData = node.extra as TypeData.Variable
        val index = variables[varData.variable]!!.first
        compileExpression(node.expr)
        mv.boxConditional(node.expr.resolvedType, varData.type)
        mv.visitVarInsn(varData.type.asmType.getOpcode(ISTORE), index)
    }

    private fun compileStructMutation(node: AstNode.StructMutation<TypeData>) {
        compileExpression(node.receiver)
        compileExpression(node.expr)

        val structType = node.receiver.resolvedType as Type.Struct
        val fieldType = structType.fields.first { it.first == node.fieldName }.second
        mv.boxConditional(node.expr.resolvedType, fieldType)
        mv.visitFieldInsn(
            PUTFIELD,
            structType.internalName,
            node.fieldName,
            fieldType.descriptor
        )
    }

    private fun compileIf(node: AstNode.If<TypeData>) {
        compileExpression(node.condition)
        val elseLabel = Label()
        val endLabel = Label()
        mv.visitJumpInsn(IFEQ, elseLabel)
        compileStatement(node.thenBranch)
        mv.visitJumpInsn(GOTO, endLabel)
        mv.visitLabel(elseLabel)
        if (node.elseBranch != null) {
            compileStatement(node.elseBranch)
        }
        mv.visitLabel(endLabel)
    }

    private fun compileWhile(node: AstNode.While<TypeData>) {
        val startLabel = Label()
        val endLabel = Label()
        mv.visitLabel(startLabel)
        compileExpression(node.condition)
        mv.visitJumpInsn(IFEQ, endLabel)
        compileStatement(node.body)
        mv.visitJumpInsn(GOTO, startLabel)
        mv.visitLabel(endLabel)
    }

    private fun compileReturn(node: AstNode.Return<TypeData>) {
        val returnType = signature.type.returnType
        if (returnType.isJavaVoid) {
            mv.visitInsn(RETURN)
        } else {
            compileExpression(node.expr!!)
            mv.boxConditional(node.expr.resolvedType, returnType)
            mv.visitInsn(returnType.asmType.getOpcode(IRETURN))
        }
    }

    fun compileExpression(node: AstNode.Expression<TypeData>) {
        addTypes(node)
        when (node) {
            is AstNode.BinaryOp -> compileBinaryOp(node)
            is AstNode.FloatLiteral -> compileFloatLiteral(node)
            is AstNode.FunctionCall -> compileFunctionCall(node)
            is AstNode.IntLiteral -> compileIntLiteral(node)
            is AstNode.BooleanLiteral -> compileBooleanLiteral(node)
            is AstNode.StringLiteral -> compileStringLiteral(node)
            is AstNode.StructLiteral -> compileStructLiteral(node)
            is AstNode.UnaryOp -> compileUnaryOp(node)
            is AstNode.Variable -> compileVariable(node)
            is AstNode.FieldAccess -> compileFieldAccess(node)
        }
    }

    private fun compileBinaryOp(node: AstNode.BinaryOp<TypeData>) {
        node.op.compile(this, node.left, node.right)
    }

    private fun compileFloatLiteral(node: AstNode.FloatLiteral<TypeData>) {
        when (node.value) {
            0.0 -> mv.visitInsn(DCONST_0)
            1.0 -> mv.visitInsn(DCONST_1)
            else -> mv.visitLdcInsn(node.value)
        }
        if (node.resolvedType == Type.Primitive.FLOAT) {
            mv.visitInsn(D2F)
        }
    }

    private fun compileBooleanLiteral(node: AstNode.BooleanLiteral<TypeData>) {
        mv.visitInsn(if (node.value) ICONST_1 else ICONST_0)
    }

    private fun compileFunctionCall(node: AstNode.FunctionCall<TypeData>) {
        val callData = node.extra as TypeData.FunctionCall
        val signature = callData.signature

        for ((argNode, argType) in node.arguments.zip(signature.type.args)) {
            compileExpression(argNode)
            mv.boxConditional(argNode.resolvedType, argType)
        }

        for (intrinsic in Intrinsics.entries) {
            if (intrinsic.signature == callData.signature) {
                intrinsic.compile(mv)
                return
            }
        }

        val name = signature.name
        if (signature != this.signature) {
            usedFunctions.add(queryEngine[Key.CompileFunction(signature)])
        }
        val source = queryEngine[Key.ResolvePackage(name.pkg)]
        val jvmName = QualifiedName(name.pkg, QualifiedName.className(source)).internalName
        mv.visitMethodInsn(INVOKESTATIC, jvmName, name.name, signature.type.internalName, false)
    }

    private fun compileIntLiteral(node: AstNode.IntLiteral<TypeData>) {
        val isLong = node.value !in Int.MIN_VALUE..Int.MAX_VALUE
        when (node.value) {
            in -1..5 -> mv.visitInsn(ICONST_0 + node.value.toInt())
            in Byte.MIN_VALUE..Byte.MAX_VALUE -> mv.visitIntInsn(BIPUSH, node.value.toInt())
            in Short.MIN_VALUE..Short.MAX_VALUE -> mv.visitIntInsn(SIPUSH, node.value.toInt())
            in Int.MIN_VALUE..Int.MAX_VALUE -> mv.visitLdcInsn(node.value.toInt())
            else -> mv.visitLdcInsn(node.value)
        }
        if (!isLong) {
            when (node.resolvedType) {
                Type.Primitive.BYTE -> mv.visitInsn(I2B)
                Type.Primitive.SHORT -> mv.visitInsn(I2S)
                Type.Primitive.LONG -> mv.visitInsn(I2L)
                else -> {}
            }
        }
    }

    private fun compileStringLiteral(node: AstNode.StringLiteral<TypeData>) {
        mv.visitLdcInsn(node.value)
    }

    private fun compileStructLiteral(node: AstNode.StructLiteral<TypeData>) {
        val structType = node.resolvedType as Type.Struct
        mv.visitTypeInsn(NEW, structType.internalName)
        mv.visitInsn(DUP)

        for ((name, type) in structType.fields) {
            val fieldNode = node.fields.find { it.first == name }!!.second
            compileExpression(fieldNode)
            mv.boxConditional(fieldNode.resolvedType, type)
        }

        mv.visitMethodInsn(
            INVOKESPECIAL,
            structType.internalName,
            "<init>",
            structType.constructorDescriptor,
            false
        )
    }

    private fun compileUnaryOp(node: AstNode.UnaryOp<TypeData>) {
        compileExpression(node.expr)
        node.op.compile(mv, node.expr.resolvedType)
    }

    private fun compileVariable(node: AstNode.Variable<TypeData>) {
        val varData = node.extra as TypeData.Variable
        val index = variables[varData.variable]!!.first
        mv.visitVarInsn(varData.type.asmType.getOpcode(ILOAD), index)
    }

    private fun compileFieldAccess(node: AstNode.FieldAccess<TypeData>) {
        val receiver = node.receiver
        compileExpression(receiver)
        mv.visitFieldInsn(
            GETFIELD,
            receiver.resolvedType.internalName,
            node.fieldName,
            node.resolvedType.descriptor
        )
    }

    private fun addTypes(node: AstNode<TypeData>) = when (val typeData = node.extra) {
        is TypeData.Basic -> addType(typeData.type)
        is TypeData.Function -> {
            addType(typeData.type.returnType)
            for (paramType in typeData.type.args) {
                addType(paramType)
            }
        }

        is TypeData.FunctionCall -> {
            addType(typeData.signature.type.returnType)
            for (paramType in typeData.signature.type.args) {
                addType(paramType)
            }
        }

        is TypeData.None -> {}
        is TypeData.Variable -> addType(typeData.type)
    }

    private fun addType(type: Type) {
        if (type is Type.Struct && type !in Type.builtinTypes) {
            val compiledStruct = queryEngine[Key.CompileStruct(type)]
            usedStructs.add(compiledStruct)
        }
    }

    class QueryProvider(private val queryEngine: QueryEngine) : Queryable<Key.CompileFunction, CompiledFunction> {
        override val keyType = Key.CompileFunction::class

        override fun query(key: Key.CompileFunction): CompiledFunction {
            val typedAst = queryEngine[Key.TypeCheckFunction(key.signature)]
            val mv = DeferredMethodVisitor()
            val compiler = FunctionCompiler(key.signature, queryEngine, mv)
            compiler.compile(typedAst)

            val pkg = key.signature.name.pkg
            val source = queryEngine[Key.ResolvePackage(pkg)]
            return CompiledFunction(
                key.signature,
                mv,
                compiler.usedFunctions,
                compiler.usedStructs,
                OutputClass.fromSource(source, pkg)
            )
        }
    }
}