package org.checkerframework.checker.genericeffects;

import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.IntersectionTypeTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.tree.WildcardTree;
import java.lang.annotation.Annotation;
import java.util.Deque;
import java.util.LinkedList;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.checkerframework.checker.compilermsgs.qual.CompilerMessageKey;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.javacutil.TreeUtils;

public class GenericEffectVisitor extends BaseTypeVisitor<GenericEffectTypeFactory> {

    protected final boolean debugSpew;
    private GenericEffectLattice genericEffect;
    private GenericEffectExtension extension;

    // effStack and currentMethods should always be the same size.
    protected final Deque<Class<? extends Annotation>> effStack;
    protected final Deque<MethodTree> currentMethods;

    // fields for compiler arguments
    boolean ignoringEffects;
    boolean ignoringWarnings;
    boolean ignoringErrors;

    /**
     * Constructor that takes passes the checker to the superclass and takes in a
     * GenericEffectExtension object. The effect stack for methods and variables are set up in the
     * constructor.
     *
     * @param checker The checker that allows the Casting Effects Checker to function.
     * @param ext An GenericEffectExtension object that provides the developer with more functions
     *     dealing with specific tree nodes.
     */
    @SuppressWarnings("JdkObsolete")
    public GenericEffectVisitor(BaseTypeChecker checker, GenericEffectExtension ext) {
        super(checker);
        assert (checker instanceof GenericEffectChecker);
        debugSpew = checker.getLintOption("debugSpew", false);

        /* ErrorProne JdkObsolete warnings are suppressed here because we must use a deque/stack implementation that permits null.
         * Without supressing this warning, ErrorProne complains we should be using ArrayDeque, which rejects null elements. */
        effStack = new LinkedList<Class<? extends Annotation>>();
        currentMethods = new LinkedList<MethodTree>();

        extension = ext;

        ignoringEffects = checker.getOption("ignoreEffects") != null;
        ignoringWarnings = checker.getOption("ignoreWarnings") != null;
        ignoringErrors = checker.getOption("ignoreErrors") != null;

        genericEffect = ((GenericEffectChecker) checker).getEffectLattice();
    }

    /**
     * Method to instantiate the factory class for the checker.
     *
     * @return The type factory of the checker.
     */
    @Override
    protected GenericEffectTypeFactory createTypeFactory() {
        return new GenericEffectTypeFactory(checker, debugSpew);
    }

    /**
     * TODO: Please document the use off this with respect to the generic effect checker better.
     * Note: The GuiEffectChecker uses a similar setup and provides more documentation.
     *
     * @param node Class declaration to process
     */
    @Override
    public void processClassTree(ClassTree node) {
        // Fix up context for static initializers of new class
        currentMethods.addFirst(null);
        effStack.addFirst(genericEffect.getBottomMostEffectInLattice());
        super.processClassTree(node);
        currentMethods.removeFirst();
        effStack.removeFirst();
    }

    /**
     * Method that visits method tree nodes and adds their effects to the stacks set up in the
     * constructor.
     *
     * @param node The method tree node that was encountered during checking.
     * @param p Void
     * @return Void
     */
    @Override
    public Void visitMethod(MethodTree node, Void p) {

        ExecutableElement methElt = TreeUtils.elementFromDeclaration(node);
        if (debugSpew) {
            System.err.println("\nVisiting method " + methElt);
        }

        assert (methElt != null);

        atypeFactory.checkEffectOverride(
                (TypeElement) methElt.getEnclosingElement(), methElt, true, node);

        currentMethods.addFirst(node);

        effStack.addFirst(genericEffect.getBottomMostEffectInLattice());

        if (debugSpew) {
            System.err.println(
                    "Pushing " + effStack.peekFirst() + " onto the stack when checking " + methElt);
        }

        Void ret = super.visitMethod(node, p);
      
	Class<? extends Annotation> targetEffect = effStack.peek();
	Class<? extends Annotation> callerEffect = atypeFactory.getDeclaredEffect(methElt);
	if (isInvalid(targetEffect, callerEffect))
		checkError(node, targetEffect, callerEffect, extension.reportError(node));
	else if (extension.reportWarning(node) != null)
		 checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
	
	currentMethods.removeFirst();
        effStack.removeFirst();
        return ret;
    }

    /**
     * Method that can be used in a visitor method to see if a node is enclosed by a method.
     *
     * @return A boolean representing whether the node is enclosed by a method (true) or not
     *     (false).
     */
    private boolean hasEnclosingMethod() {
        MethodTree callerTree = TreeUtils.enclosingMethod(getCurrentPath());
        return callerTree != null;
    }

    /**
     * Method to check is a target effect and a caller effect are invalid according to the lattice.
     * The method also checks which effects are to be ignored.
     *
     * @param targetEffect Target effect of node.
     * @param callerEffect Caller effect of node.
     * @return Boolean value representing whether the effects are invalid (true) or not (false)
     */
    private boolean isInvalid(
            Class<? extends Annotation> targetEffect, Class<? extends Annotation> callerEffect) {
        if (ignoringEffects)
            targetEffect =
                    extension.checkIgnoredEffects(checker.getOption("ignoreEffects"), targetEffect);
        if (!genericEffect.LE(targetEffect, callerEffect)) return true;
        return false;
    }

    /**
     * Method that reports an error as specified by given parameters. The method also checks which
     * errors are to be ignored.
     *
     * @param node Node for which error should be reported.
     * @param targetEffect Target effect of node.
     * @param callerEffect Caller effect of node.
     * @param failureMsg Error message to be reported.
     */
    private void checkError(
            Tree node,
            Class<? extends Annotation> targetEffect,
            Class<? extends Annotation> callerEffect,
            @CompilerMessageKey String failureMsg) {
        if (!ignoringErrors) checker.reportError(node, failureMsg, targetEffect, callerEffect);
        else if (ignoringErrors
                && !extension.isIgnored(checker.getOption("ignoreErrors"), failureMsg))
            checker.reportError(node, failureMsg, targetEffect, callerEffect);
    }

    /**
     * Method that reports a warning as specified by the given parameters. The method also checks
     * which warnings are to be ignored.
     *
     * @param node Node for which warning should be reported.
     * @param targetEffect Target effect of node.
     * @param callerEffect Caller effect of node.
     * @param warningMsg Warning message to be reported.
     */
    private void checkWarning(
            Tree node,
            Class<? extends Annotation> targetEffect,
            Class<? extends Annotation> callerEffect,
            @CompilerMessageKey String warningMsg) {
        if (!ignoringWarnings) checker.reportWarning(node, warningMsg, targetEffect, callerEffect);
        else if (ignoringWarnings
                && !extension.isIgnored(checker.getOption("ignoreWarnings"), warningMsg))
            checker.reportWarning(node, warningMsg, targetEffect, callerEffect);
    }

    /**
     * Method that is used by visitor methods to get the effect of a method that a node is within.
     *
     * @return Effect of a method that a node is within.
     */
    private Class<? extends Annotation> getMethodCallerEffect() {
        MethodTree callerTree = TreeUtils.enclosingMethod(getCurrentPath());
        ExecutableElement callerElt = TreeUtils.elementFromDeclaration(callerTree);
        return atypeFactory.getDeclaredEffect(callerElt);
    }

    /**
     * Method that is used in a visitor method to get the default effect a class that a node is
     * within.
     *
     * @return The default effect of a class that a node is within.
     */
    private Class<? extends Annotation> getDefaultClassEffect() {
        ClassTree clsTree = TreeUtils.enclosingClass(getCurrentPath());
        Element clsElt = TreeUtils.elementFromDeclaration(clsTree);
        return atypeFactory.getDefaultEffect(clsElt);
    }

    /**
     * Method that visits all the method invocation tree nodes and raises failures/warnings for
     * unsafe method invocations.
     *
     * @param node Method invocation tree node that is found during checking.
     * @param p Void
     * @return Void
     */
    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
	effStack.push(genericEffect.getBottomMostEffectInLattice());
	scan(node.getMethodSelect(), p);
	Class<? extends Annotation> x = effStack.pop();
	for(Tree args : node.getArguments()){
            effStack.push(genericEffect.getBottomMostEffectInLattice());
	    scan(args, p);
	    Class<? extends Annotation> argEffect = effStack.pop();
	    x = genericEffect.seq(x,argEffect);
	}
	x = genericEffect.seq(x, getMethodCallerEffect());
	effStack.push(x);	
	    
        return super.visitMethodInvocation(node, p);
    }

    /**
     * Method to check if the constructor call is made from a valid context.
     *
     * @param node New class tree node that is found during checking.
     * @param p Void
     * @return Void
     */
    @Override
    public Void visitNewClass(NewClassTree node, Void p) {
        if (hasEnclosingMethod()) {
            ExecutableElement elt = TreeUtils.elementFromUse(node);
            Class<? extends Annotation> targetEffect = atypeFactory.getDeclaredEffect(elt);
            Class<? extends Annotation> callerEffect = getMethodCallerEffect();
            if (isInvalid(targetEffect, callerEffect))
                checkError(node, targetEffect, callerEffect, "constructor.call.invalid");
        } else {
            ExecutableElement elt = TreeUtils.elementFromUse(node);
            Class<? extends Annotation> targetEffect = atypeFactory.getDeclaredEffect(elt);
            Class<? extends Annotation> callerEffect = getDefaultClassEffect();
            if (isInvalid(targetEffect, callerEffect))
                checkError(node, targetEffect, callerEffect, "constructor.call.invalid");
        }
        return super.visitNewClass(node, p);
    }

    /**
     * The methods below this comment follow the same format. Each method is a different visit
     * method for a different kind of tree node. Using the extensions class the developer can
     * activate specific visitor methods depending on what they want to check.
     *
     * <p>The methods work by first checking if the node being checked is enclosed by a method. If
     * it is then the method obtains the effect of the node and checks it against the method's
     * effect. If the node is not enclosed by a method, then it checks at the variable level against
     * the class annotation.
     *
     * @param node Specific tree node that is to be checked.
     * @param p Void
     * @return Void
     */
    @Override
    public Void visitArrayAccess(ArrayAccessTree node, Void p) {
        // checks if check is active
        if (extension.doesArrayAccessCheck()) {
            // checks if node is enclosed by method
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkArrayAccess(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                // checks if the effect of the node is less than or equal to the methods
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                // checks if the node should output any warnings
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
            // if node is not within a method, then node is compared to default effect of class
            else {
                Class<? extends Annotation> targetEffect = extension.checkArrayAccess(node);
                // gets node's default effect
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                // checks if effect of the node is less than or equal to the default effects
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                // checks if the node should output and warnings
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitArrayAccess(node, p);
    }

    @Override
    public Void visitArrayType(ArrayTypeTree node, Void p) {
        if (extension.doesArrayTypeCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkArrayType(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkArrayType(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitArrayType(node, p);
    }

    @Override
    public Void visitAssert(AssertTree node, Void p) {
        if (extension.doesAssertCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkAssert(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkAssert(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitAssert(node, p);
    }

    @Override
    public Void visitAssignment(AssignmentTree node, Void p) {
        if (extension.doesAssignmentCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkAssignment(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkAssignment(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitAssignment(node, p);
    }

    @Override
    public Void visitBinary(BinaryTree node, Void p) {
        if (extension.doesBinaryCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkBinary(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkBinary(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitBinary(node, p);
    }

    @Override
    public Void visitBreak(BreakTree node, Void p) {
        if (extension.doesBreakCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkBreak(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkBreak(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitBreak(node, p);
    }

    @Override
    public Void visitCase(CaseTree node, Void p) {
        if (extension.doesCaseCheck()) {
            Class<? extends Annotation> topEffect = effStack.pop();
            Class<? extends Annotation> currentEffect = getMethodCallerEffect();
            Class<? extends Annotation> leastUpperBound = genericEffect.LUB(topEffect, currentEffect);
            effStack.addFirst(leastUpperBound);

	}
        return super.visitCase(node, p);
    }

    @Override
    public Void visitCatch(CatchTree node, Void p) {
        if (extension.doesCatchCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkCatch(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkCatch(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitCatch(node, p);
    }

    @Override
    public Void visitCompoundAssignment(CompoundAssignmentTree node, Void p) {
        if (extension.doesCompoundAssignmentCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkCompoundAssignment(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkCompoundAssignment(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitCompoundAssignment(node, p);
    }

    @Override
    public Void visitConditionalExpression(ConditionalExpressionTree node, Void p) {
        if (extension.doesConditionalExpressionCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect =
                        extension.checkConditionalExpression(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect =
                        extension.checkConditionalExpression(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitConditionalExpression(node, p);
    }

    @Override
    public Void visitContinue(ContinueTree node, Void p) {
        if (extension.doesContinueCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkContinue(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkContinue(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitContinue(node, p);
    }

    @Override
    public Void visitDoWhileLoop(DoWhileLoopTree node, Void p) {
        if (extension.doesDoWhileLoopCheck()) {
            //Condition
            effStack.push(genericEffect.getBottomMostEffectInLattice());
            scan(node.getCondition(), p);
            Class<? extends Annotation> Xc = effStack.pop();
            //Statement
            effStack.push(genericEffect.getBottomMostEffectInLattice());
            scan(node.getStatement(), p);
            Class<? extends Annotation> Xs = effStack.pop();
	    
	    Class<? extends Annotation> x = genericEffect.seq(Xc, Xs);
	    effStack.push(x);
	}
        return super.visitDoWhileLoop(node, p);
    }

    @Override
    public Void visitEnhancedForLoop(EnhancedForLoopTree node, Void p) {
        if (extension.doesEnhancedForLoopCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkEnhancedForLoop(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkEnhancedForLoop(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitEnhancedForLoop(node, p);
    }

    @Override
    public Void visitForLoop(ForLoopTree node, Void p) {
        if (extension.doesForLoopCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkForLoop(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkForLoop(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitForLoop(node, p);
    }

    @Override
    public Void visitIf(IfTree node, Void p) {
        if (extension.doesIfCheck()) {
	    //Condition
            effStack.push(genericEffect.getBottomMostEffectInLattice());
	    scan(node.getCondition(), p); 
	    Class<? extends Annotation> Xc = effStack.pop();
	    //If
	    effStack.push(genericEffect.getBottomMostEffectInLattice());
            scan(node.getThenStatement(), p);
            Class<? extends Annotation> X1 = effStack.pop();
	    //Else
	    effStack.push(genericEffect.getBottomMostEffectInLattice());
            scan(node.getElseStatement(), p);
            Class<? extends Annotation> X2 = effStack.pop();
	    
	    effStack.push(genericEffect.seq(Xc, genericEffect.LUB(X1,X2)));
		
	}
        return super.visitIf(node, p);
    }

    @Override
    public Void visitInstanceOf(InstanceOfTree node, Void p) {
        if (extension.doesInstanceOfCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkInstanceOf(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkInstanceOf(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitInstanceOf(node, p);
    }

    @Override
    public Void visitIntersectionType(IntersectionTypeTree node, Void p) {
        if (extension.doesIntersectionTypeCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkIntersectionType(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkIntersectionType(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitIntersectionType(node, p);
    }

    @Override
    public Void visitLabeledStatement(LabeledStatementTree node, Void p) {
        if (extension.doesLabeledStatementCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkLabeledStatement(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkLabeledStatement(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitLabeledStatement(node, p);
    }

    @Override
    public Void visitLiteral(LiteralTree node, Void p) {
        if (extension.doesLiteralCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkLiteral(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkLiteral(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitLiteral(node, p);
    }

    @Override
    public Void visitNewArray(NewArrayTree node, Void p) {
        if (extension.doesNewArrayCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkNewArray(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkNewArray(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitNewArray(node, p);
    }

    @Override
    public Void visitPrimitiveType(PrimitiveTypeTree node, Void p) {
        if (extension.doesPrimitiveTypeCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkPrimitiveType(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkPrimitiveType(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitPrimitiveType(node, p);
    }

    @Override
    public Void visitReturn(ReturnTree node, Void p) {
        if (extension.doesReturnCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkReturn(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkReturn(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitReturn(node, p);
    }

    @Override
    public Void visitSwitch(SwitchTree node, Void p) {
        if (extension.doesSwitchCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkSwitch(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkSwitch(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitSwitch(node, p);
    }

    @Override
    public Void visitSynchronized(SynchronizedTree node, Void p) {
        if (extension.doesSynchronizedCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkSynchronized(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkSynchronized(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitSynchronized(node, p);
    }

    @Override
    public Void visitThrow(ThrowTree node, Void p) {
        if (extension.doesThrowCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkThrow(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkThrow(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitThrow(node, p);
    }

    @Override
    public Void visitTry(TryTree node, Void p) {
        if (extension.doesTryCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkTry(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkTry(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitTry(node, p);
    }

    @Override
    public Void visitTypeCast(TypeCastTree node, Void p) {
        if (extension.doesTypeCastCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkTypeCast(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkTypeCast(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
            return super.visitTypeCast(node, p);
        }
        return null;
    }

    @Override
    public Void visitUnary(UnaryTree node, Void p) {
        if (extension.doesUnaryCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkUnary(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> varTargetEffect = extension.checkUnary(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(varTargetEffect, callerEffect))
                    checkError(node, varTargetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(
                            node, varTargetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitUnary(node, p);
    }

    @Override
    public Void visitUnionType(UnionTypeTree node, Void p) {
        if (extension.doesUnionTypeCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkUnionType(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkUnionType(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitUnionType(node, p);
    }

    @Override
    public Void visitWhileLoop(WhileLoopTree node, Void p) {
        if (extension.doesWhileLoopCheck()) {
            if (hasEnclosingMethod()) {
                if (hasEnclosingMethod()) {
                    Class<? extends Annotation> targetEffect = extension.checkWhileLoop(node);
                    Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                    if (isInvalid(targetEffect, callerEffect))
                        checkError(node, targetEffect, callerEffect, extension.reportError(node));
                    else if (extension.reportWarning(node) != null)
                        checkWarning(
                                node, targetEffect, callerEffect, extension.reportWarning(node));
                } else {
                    Class<? extends Annotation> targetEffect = extension.checkWhileLoop(node);
                    Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                    if (isInvalid(targetEffect, callerEffect))
                        checkError(node, targetEffect, callerEffect, extension.reportError(node));
                    else if (extension.reportWarning(node) != null)
                        checkWarning(
                                node, targetEffect, callerEffect, extension.reportWarning(node));
                }
            }
        }
        return super.visitWhileLoop(node, p);
    }

    @Override
    public Void visitWildcard(WildcardTree node, Void p) {
        if (extension.doesWildcardCheck()) {
            if (hasEnclosingMethod()) {
                Class<? extends Annotation> targetEffect = extension.checkWildcard(node);
                Class<? extends Annotation> callerEffect = getMethodCallerEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            } else {
                Class<? extends Annotation> targetEffect = extension.checkWildcard(node);
                Class<? extends Annotation> callerEffect = getDefaultClassEffect();
                if (isInvalid(targetEffect, callerEffect))
                    checkError(node, targetEffect, callerEffect, extension.reportError(node));
                else if (extension.reportWarning(node) != null)
                    checkWarning(node, targetEffect, callerEffect, extension.reportWarning(node));
            }
        }
        return super.visitWildcard(node, p);
    }
}
