package cop5555sp15.ast;

import static cop5555sp15.TokenStream.Kind.AND;
import static cop5555sp15.TokenStream.Kind.BAR;
import static cop5555sp15.TokenStream.Kind.DIV;
import static cop5555sp15.TokenStream.Kind.EQUAL;
import static cop5555sp15.TokenStream.Kind.GE;
import static cop5555sp15.TokenStream.Kind.GT;
import static cop5555sp15.TokenStream.Kind.LE;
import static cop5555sp15.TokenStream.Kind.LSHIFT;
import static cop5555sp15.TokenStream.Kind.LT;
import static cop5555sp15.TokenStream.Kind.MINUS;
import static cop5555sp15.TokenStream.Kind.NOTEQUAL;
import static cop5555sp15.TokenStream.Kind.PLUS;
import static cop5555sp15.TokenStream.Kind.RSHIFT;
import static cop5555sp15.TokenStream.Kind.TIMES;

import org.objectweb.asm.*;

import cop5555sp15.TokenStream.Kind;
import cop5555sp15.ast.TypeCheckVisitor.TypeCheckException;
import cop5555sp15.TypeConstants;

public class CodeGenVisitor implements ASTVisitor, Opcodes, TypeConstants {

	ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
	// Because we used the COMPUTE_FRAMES flag, we do not need to
	// insert the mv.visitFrame calls that you will see in some of the
	// asmifier examples. ASM will insert those for us.
	// FYI, the purpose of those instructions is to provide information
	// about what is on the stack just before each branch target in order
	// to speed up class verification.
	FieldVisitor fv;
	String className;
	String classDescriptor;

	// This class holds all attributes that need to be passed downwards as the
	// AST is traversed. Initially, it only holds the current MethodVisitor.
	// Later, we may add more attributes.
	static class InheritedAttributes {
		public InheritedAttributes(MethodVisitor mv) {
			super();
			this.mv = mv;
		}

		MethodVisitor mv;
	}

	@Override
	public Object visitVarDec(VarDec varDec, Object arg) throws Exception {
		String varName = varDec.identToken.getText();
		String varType = (String) varDec.type.visit(this, arg);		
		{
			fv = cw.visitField(0, varName, varType, null, null);
			fv.visitEnd();
		}
		return null;
	}
	
	@Override
	public Object visitAssignmentStatement(
			AssignmentStatement assignmentStatement, Object arg)
			throws Exception {
		MethodVisitor mv = ((InheritedAttributes) arg).mv;		
		String varName = (String) assignmentStatement.lvalue.visit(this, arg);
		mv.visitVarInsn(ALOAD, 0);
		assignmentStatement.expression.visit(this, arg);
		String varType = assignmentStatement.expression.getType();
		mv.visitFieldInsn(PUTFIELD, className, varName, varType);
		return null;
	}	

	@Override
	public Object visitIdentLValue(IdentLValue identLValue, Object arg)
			throws Exception {
		return identLValue.identToken.getText();
	}
	
	@Override
	public Object visitIdentExpression(IdentExpression identExpression,
			Object arg) throws Exception {
		String varName = identExpression.identToken.getText();	
		String varType = identExpression.getType();
//		throw new UnsupportedOperationException("code generation not yet implemented");
		MethodVisitor mv = ((InheritedAttributes) arg).mv;	
		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(GETFIELD, className, varName, varType);
		return null;
	}
	
	@Override
	public Object visitIntLitExpression(IntLitExpression intLitExpression,
			Object arg) throws Exception {
		// this should be the first statement of all visit methods that generate instructions
		MethodVisitor mv = ((InheritedAttributes) arg).mv; 
		mv.visitLdcInsn(intLitExpression.value);
		return null;
	}

	@Override
	public Object visitBinaryExpression(BinaryExpression binaryExpression,
			Object arg) throws Exception {
		MethodVisitor mv = ((InheritedAttributes) arg).mv;		
		String exprType = (String) binaryExpression.expression0.getType();		
		Kind op = binaryExpression.op.kind;		
		
		if (op == PLUS && exprType == stringType) {					
			mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
			mv.visitInsn(DUP);
			binaryExpression.expression0.visit(this,arg);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
			binaryExpression.expression1.visit(this,arg);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
		} else if ((op == PLUS || op == MINUS || op == TIMES || op == DIV) && exprType == intType) {
			binaryExpression.expression0.visit(this,arg);
			binaryExpression.expression1.visit(this,arg);
			switch(op) {
			case PLUS:
				mv.visitInsn(IADD);
				break;
			case MINUS:
				mv.visitInsn(ISUB);
				break;
			case TIMES:
				mv.visitInsn(IMUL);
				break;
			case DIV:
				mv.visitInsn(IDIV);
				break;
			default:
				throw new UnsupportedOperationException("code generation not yet implemented");
			}
		} else if (op == EQUAL) {
			binaryExpression.expression0.visit(this,arg);
			binaryExpression.expression1.visit(this,arg);
			Label le1 = new Label();
			if(exprType == booleanType || exprType == intType) {
				mv.visitJumpInsn(IF_ICMPNE, le1);				
			}			
			else if(exprType == stringType) {
				mv.visitJumpInsn(IF_ACMPNE, le1);				
			}		
			mv.visitInsn(ICONST_1);
			Label le2 = new Label();
			mv.visitJumpInsn(GOTO, le2);
			mv.visitLabel(le1);
			mv.visitInsn(ICONST_0);
			mv.visitLabel(le2);
		} else if (op == NOTEQUAL) {
			binaryExpression.expression0.visit(this,arg);
			binaryExpression.expression1.visit(this,arg);
			Label l1 = new Label();
			if(exprType == booleanType || exprType == intType) {
				mv.visitJumpInsn(IF_ICMPEQ, l1);				
			}			
			else if(exprType == stringType) {
				mv.visitJumpInsn(IF_ACMPEQ, l1);				
			}		
			mv.visitInsn(ICONST_1);
			Label l2 = new Label();
			mv.visitJumpInsn(GOTO, l2);
			mv.visitLabel(l1);
			mv.visitInsn(ICONST_0);
			mv.visitLabel(l2);
		} else if (op ==  LT || op == GT || op == LE || op == GE) {
			binaryExpression.expression0.visit(this,arg);
			binaryExpression.expression1.visit(this,arg);
			Label l1 = new Label();
			switch(op) {
			case LT: 
				mv.visitJumpInsn(IF_ICMPGE, l1);
				break;
			case GT:
				mv.visitJumpInsn(IF_ICMPLE, l1);
				break;
			case LE: 
				mv.visitJumpInsn(IF_ICMPGT, l1);
				break;
			case GE:	
				mv.visitJumpInsn(IF_ICMPLT, l1);
				break;
			default:
				throw new UnsupportedOperationException("code generation not yet implemented");
			}						
			mv.visitInsn(ICONST_1);
			Label l2 = new Label();
			mv.visitJumpInsn(GOTO, l2);
			mv.visitLabel(l1);
			mv.visitInsn(ICONST_0);
			mv.visitLabel(l2);
		} else if (op == BAR || op == AND) {
			binaryExpression.expression0.visit(this, arg);
			Label l = new Label();
			mv.visitJumpInsn(IFEQ, l);
			binaryExpression.expression1.visit(this,arg);
			mv.visitJumpInsn(IFEQ, l);
			mv.visitInsn(ICONST_1);
			Label lo2 = new Label();
			mv.visitJumpInsn(GOTO, lo2);
			mv.visitLabel(l);
			mv.visitInsn(ICONST_0);
			mv.visitLabel(lo2);
		} else {
			throw new UnsupportedOperationException("code generation not yet implemented");
		}
		return null;
	}

	@Override
	public Object visitBlock(Block block, Object arg) throws Exception {
		for (BlockElem elem : block.elems) {
			elem.visit(this, arg);
		}
		return null;
	}

	@Override
	public Object visitBooleanLitExpression(
			BooleanLitExpression booleanLitExpression, Object arg)
			throws Exception {
		MethodVisitor mv = ((InheritedAttributes) arg).mv;
		if (booleanLitExpression.value == true) {
			mv.visitInsn(ICONST_1);
		} else {
			mv.visitInsn(ICONST_0);
		}	
		return null;
	}

	@Override
	public Object visitClosure(Closure closure, Object arg) throws Exception {
		for (VarDec dec : closure.formalArgList) {
			dec.visit(this, arg);
		}
		for (Statement statement : closure.statementList) {
			statement.visit(this, arg);
		}
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitClosureDec(ClosureDec closureDeclaration, Object arg)
			throws Exception {
		closureDeclaration.closure.visit(this, arg);
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitClosureEvalExpression(
			ClosureEvalExpression closureExpression, Object arg)
			throws Exception {
		for (Expression e : closureExpression.expressionList) {
			e.visit(this, arg);
		}
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitClosureExpression(ClosureExpression closureExpression,
			Object arg) throws Exception {
		closureExpression.closure.visit(this, arg);
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitExpressionLValue(ExpressionLValue expressionLValue,
			Object arg) throws Exception {
//		MethodVisitor mv = ((InheritedAttributes) arg).mv;
//		String varName = expressionLValue.identToken.getText();	
//		String varType = expressionLValue.expression.getType();
//		expressionLValue.expression.visit(this, arg);
//		mv.visitFieldInsn(GETSTATIC, className, varName, varType);			
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitExpressionStatement(
			ExpressionStatement expressionStatement, Object arg)
			throws Exception {
		expressionStatement.expression.visit(this, arg);
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}	

	@Override
	public Object visitIfElseStatement(IfElseStatement ifElseStatement,
			Object arg) throws Exception {
		ifElseStatement.expression.visit(this, arg);
		ifElseStatement.ifBlock.visit(this, arg);
		ifElseStatement.elseBlock.visit(this, arg);
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitIfStatement(IfStatement ifStatement, Object arg)
			throws Exception {
		ifStatement.expression.visit(this, arg);
		ifStatement.block.visit(this, arg);
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	

	@Override
	public Object visitKeyExpression(KeyExpression keyExpression, Object arg)
			throws Exception {
		keyExpression.expression.visit(this, arg);
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitKeyValueExpression(
			KeyValueExpression keyValueExpression, Object arg) throws Exception {
		keyValueExpression.key.visit(this, arg);
		keyValueExpression.value.visit(this, arg);
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitKeyValueType(KeyValueType keyValueType, Object arg)
			throws Exception {
		keyValueType.keyType.visit(this, arg);
		keyValueType.valueType.visit(this, arg);
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitListExpression(ListExpression listExpression, Object arg)
			throws Exception {
		for (Expression e : listExpression.expressionList) {
			e.visit(this, arg);
		}
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitListOrMapElemExpression(
			ListOrMapElemExpression listOrMapElemExpression, Object arg)
			throws Exception {
		listOrMapElemExpression.expression.visit(this, arg);
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitListType(ListType listType, Object arg) throws Exception {
		listType.type.visit(this, arg);
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitMapListExpression(MapListExpression mapListExpression,
			Object arg) throws Exception {
		for (Expression e : mapListExpression.mapList) {
			e.visit(this, arg);
		}
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitPrintStatement(PrintStatement printStatement, Object arg)
			throws Exception {
		MethodVisitor mv = ((InheritedAttributes) arg).mv;
		Label l0 = new Label();
		mv.visitLabel(l0);
		mv.visitLineNumber(printStatement.firstToken.getLineNumber(), l0);
		mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out",
				"Ljava/io/PrintStream;");
		printStatement.expression.visit(this, arg); // adds code to leave value
													// of expression on top of
													// stack.
													// Unless there is a good
													// reason to do otherwise,
													// pass arg down the tree
		String etype = printStatement.expression.getType();
		if (etype.equals("I") || etype.equals("Z")
				|| etype.equals("Ljava/lang/String;")) {
			String desc = "(" + etype + ")V";
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println",
					desc, false);
		} else
			throw new UnsupportedOperationException(
					"printing list or map not yet implemented");
		return null;
	}

	@Override
	public Object visitProgram(Program program, Object arg) throws Exception {
		className = program.JVMName;
		classDescriptor = 'L' + className + ';';
		cw.visit(52, // version
				ACC_PUBLIC + ACC_SUPER, // access codes
				className, // fully qualified classname
				null, // signature
				"java/lang/Object", // superclass
				new String[] { "cop5555sp15/Codelet" } // implemented interfaces
		);
		cw.visitSource(null, null); // maybe replace first argument with source
									// file name

		// create init method
		{
			MethodVisitor mv;
			mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
			mv.visitCode();
			Label l0 = new Label();
			mv.visitLabel(l0);
			mv.visitLineNumber(3, l0);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>",
					"()V", false);
			mv.visitInsn(RETURN);
			Label l1 = new Label();
			mv.visitLabel(l1);
			mv.visitLocalVariable("this", classDescriptor, null, l0, l1, 0);
			mv.visitMaxs(1, 1);
			mv.visitEnd();
		}

		// generate the execute method
		MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "execute", // name of top
																	// level
																	// method
				"()V", // descriptor: this method is parameterless with no
						// return value
				null, // signature.  This is null for us, it has to do with generic types
				null // array of strings containing exceptions
				);
		mv.visitCode();
		Label lbeg = new Label();
		mv.visitLabel(lbeg);
		mv.visitLineNumber(program.firstToken.lineNumber, lbeg);
		program.block.visit(this, new InheritedAttributes(mv));
		mv.visitInsn(RETURN);
		Label lend = new Label();
		mv.visitLabel(lend);
		mv.visitLocalVariable("this", classDescriptor, null, lbeg, lend, 0);
		mv.visitMaxs(0, 0);  //this is required just before the end of a method. 
		                     //It causes asm to calculate information about the
		                     //stack usage of this method.
		mv.visitEnd();

		
		cw.visitEnd();
		return cw.toByteArray();
	}

	@Override
	public Object visitQualifiedName(QualifiedName qualifiedName, Object arg) {
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitRangeExpression(RangeExpression rangeExpression,
			Object arg) throws Exception {
		rangeExpression.lower.visit(this, arg);
		rangeExpression.upper.visit(this, arg);
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitReturnStatement(ReturnStatement returnStatement,
			Object arg) throws Exception {
		returnStatement.expression.visit(this, arg);
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitSimpleType(SimpleType simpleType, Object arg)
			throws Exception {
		return simpleType.getJVMType();
	}

	@Override
	public Object visitSizeExpression(SizeExpression sizeExpression, Object arg)
			throws Exception {
		sizeExpression.expression.visit(this, arg);
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitStringLitExpression(
			StringLitExpression stringLitExpression, Object arg)
			throws Exception {
		MethodVisitor mv = ((InheritedAttributes) arg).mv; 
		mv.visitLdcInsn(stringLitExpression.value);
		return null;
	}

	@Override
	public Object visitUnaryExpression(UnaryExpression unaryExpression,
			Object arg) throws Exception {
		unaryExpression.expression.visit(this, arg);
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitValueExpression(ValueExpression valueExpression,
			Object arg) throws Exception {
		valueExpression.expression.visit(this, arg);
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitWhileRangeStatement(
			WhileRangeStatement whileRangeStatement, Object arg)
			throws Exception {
		whileRangeStatement.rangeExpression.visit(this, arg);
		whileRangeStatement.block.visit(this, arg);
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitWhileStarStatement(WhileStarStatement whileStarStatement,
			Object arg) throws Exception {
		whileStarStatement.expression.visit(this, arg);
		whileStarStatement.block.visit(this, arg);
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitWhileStatement(WhileStatement whileStatement, Object arg)
			throws Exception {
		whileStatement.expression.visit(this, arg);
		whileStatement.block.visit(this, arg);
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

	@Override
	public Object visitUndeclaredType(UndeclaredType undeclaredType, Object arg)
			throws Exception {
		throw new UnsupportedOperationException(
				"code generation not yet implemented");
	}

}
