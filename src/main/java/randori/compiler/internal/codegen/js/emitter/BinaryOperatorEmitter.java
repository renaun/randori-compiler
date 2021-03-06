/***
 * Copyright 2013 Teoti Graphix, LLC.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * 
 * @author Michael Schmalle <mschmalle@teotigraphix.com>
 */

package randori.compiler.internal.codegen.js.emitter;

import org.apache.flex.compiler.definitions.IAccessorDefinition;
import org.apache.flex.compiler.definitions.IDefinition;
import org.apache.flex.compiler.definitions.IFunctionDefinition;
import org.apache.flex.compiler.definitions.ITypeDefinition;
import org.apache.flex.compiler.internal.tree.as.BinaryOperatorAssignmentNode;
import org.apache.flex.compiler.internal.tree.as.IdentifierNode;
import org.apache.flex.compiler.projects.ICompilerProject;
import org.apache.flex.compiler.tree.ASTNodeID;
import org.apache.flex.compiler.tree.as.*;

import randori.compiler.codegen.js.IRandoriEmitter;
import randori.compiler.codegen.js.ISubEmitter;
import randori.compiler.internal.utils.*;

import org.apache.flex.compiler.internal.tree.as.ExpressionNodeBase;
/**
 * Handles the production of the {@link IBinaryOperatorNode}.
 * 
 * @author Michael Schmalle
 */
public class BinaryOperatorEmitter extends BaseSubEmitter implements
        ISubEmitter<IBinaryOperatorNode>
{

    public BinaryOperatorEmitter(IRandoriEmitter emitter)
    {
        super(emitter);
    }

    @Override
    public void emit(IBinaryOperatorNode node)
    {
        // THIS IS HARD CODED "filter" but maybe needs to be dynamic at some point
        // "filter" is applied to E4X expressions and is implemented in JXONTree
        boolean isFilter = false;
        if (node instanceof ExpressionNodeBase)
        {
            //  Ensure we're not in a with scope or part of a filter expression.
            final ExpressionNodeBase expressionNode = (ExpressionNodeBase)node;
            if (expressionNode.inFilter() && expressionNode.hasParenthesis())
            {
                isFilter = true;
                write("filter");
            }
        }

        ICompilerProject project = getEmitter().getWalker().getProject();

        IExpressionNode left = node.getLeftOperandNode();
        IDefinition lhsDefinition = left.resolve(project);

        IExpressionNode right = node.getRightOperandNode();
        IDefinition rhsDefinition = right.resolve(project);

        // This is a special case for array assessor on flash.utils.ByteArray
        // when an assignment is being made. Array assessor for retrieving data is separate
        if (left.getNodeID() == ASTNodeID.ArrayIndexExpressionID
            && node.getOperator().getOperatorText() == "=")
        {
            IDynamicAccessNode arrayNode = (IDynamicAccessNode)left;
            IDefinition definition = arrayNode.getLeftOperandNode().resolve(project);
            if (definition != null && arrayNode.getLeftOperandNode() instanceof IdentifierNode && definition.getTypeReference() != null
                    && definition.getTypeReference().getName().indexOf("ByteArray") > -1)
            {
                //System.out.println("**** ASSIGNMENT WITH ARRAY ASSESSOR ****");
                // TODO Check for ByteArray builtin
                getModel().setInAssignment(false);
                getEmitter().getWalker().walk(arrayNode.getLeftOperandNode());
                write(".setValueByPosition(");
                getEmitter().getWalker().walk(arrayNode.getRightOperandNode());
                write(",");
                // TODO not sure if need to do same checks as below
                getEmitter().getWalker().walk(right);
                write(")");
                return;
            }
        }

        // Compound statements
        if (ExpressionUtils.isCompoundAssignment(node, lhsDefinition))
        {
            emitCompoundAssignment(node, lhsDefinition, right);
            return;
        }

        if (ASNodeUtils.hasParenOpen(node))
            write("(");
        if (isFilter)
            write("'");

        // if on the left side with '=' , we are in setter mode
        getModel().setInAssignment(ExpressionUtils.isInAssignment(node));
        getModel().setAssign(node);

        // Case where right is another BinaryOperatorAssignment
        if (right instanceof BinaryOperatorAssignmentNode)
        {
            emitBinaryRightAssignment(rhsDefinition, right);
            writeNewline(";");

            // Need to reset to this parent node
            getModel().setInAssignment(ExpressionUtils.isInAssignment(node));
            getModel().setAssign(node);
        }

        getEmitter().getWalker().walk(left);

        if (!MetaDataUtils.isNative(lhsDefinition)
                && getModel().isInAssignment()
                && lhsDefinition instanceof IAccessorDefinition)
        {
            if (getModel().isCall())
                write("(this, ");
            else
                write("(");
            getModel().setCall(false);
        }
        else
        {
            // if not in assignment with setter, write the operator
            if (node.getNodeID() != ASTNodeID.Op_CommaID)
                write(" ");
            write(node.getOperator().getOperatorText());
            write(" ");
        }

        boolean wasAssignment = getModel().isInAssignment();
        getModel().setInAssignment(false);

        // Right was another Binary Assignment so we just need the getter to set this left side
        // TODO this is still potentially prone to side affects in the getter functions
        if (right instanceof BinaryOperatorAssignmentNode)
            getEmitter().getWalker().walk(((IBinaryOperatorNode)right).getLeftOperandNode());
        else
            emitBinaryRightAssignment(rhsDefinition, right);

        if (!MetaDataUtils.isNative(lhsDefinition) && wasAssignment
                && lhsDefinition instanceof IAccessorDefinition)
        {
            writeIfNotNative(")", lhsDefinition);
        }

        if (isFilter)
            write("'");
        if (ASNodeUtils.hasParenClose(node))
            write(")");
    }

    private void emitBinaryRightAssignment(IDefinition rhsDefinition, IExpressionNode right)
    {

        if (rhsDefinition instanceof IFunctionDefinition
                && right instanceof IIdentifierNode)
        {

            // this is not a right hand function call, just a reff to accessor or function

            String pre = "this";
            String parentQName = DefinitionNameUtils.toExportQualifiedName(
                    rhsDefinition.getParent(), getProject());
            if (rhsDefinition.isStatic())
            {
                pre = parentQName;
            }
            String name = getEmitter().stringifyNode(right);
            if (name.contains("get_")
                    || name.contains("set_"))
            {
                getEmitter().getWalker().walk(right);
            }
            else
            {
                name = "";
                write(IRandoriEmitter.STATIC_DELEGATE_NAME);
                write("(" + pre + ", ");
                write(pre);
                write(".");
                write(name + rhsDefinition.getBaseName());
                write(")");
            }
        }
        //        else if (rhsDefinition instanceof IVariableDefinition
        //                && right instanceof IIdentifierNode)
        //        {
        //            write(IRandoriEmitter.STATIC_DELEGATE_NAME);
        //            String pre = "this";
        //            String parentQName = DefinitionNameUtils.toExportQualifiedName(
        //                    rhsDefinition.getParent(), getProject());
        //            if (rhsDefinition.isStatic())
        //            {
        //                pre = parentQName;
        //            }
        //            write("(" + pre + ", ");
        //            write(pre);
        //            write(".");
        //            write(rhsDefinition.getBaseName());
        //            write(")");
        //        }
        else
        {
            getEmitter().getWalker().walk(right);
        }

        RandoriUtils.addBinaryRightDependency(right, getModel(), getProject());
    }

    private void emitCompoundAssignment(IBinaryOperatorNode node,
            IDefinition left, IExpressionNode right)
    {
        IAccessorDefinition accessor = (IAccessorDefinition) left;

        String prefix = "this";
        if (node.getLeftOperandNode().getNodeID() == ASTNodeID.MemberAccessExpressionID)
        {
            IExpressionNode farLeft = ((IMemberAccessExpressionNode) node
                    .getLeftOperandNode()).getLeftOperandNode();
            if (farLeft.getNodeID() == ASTNodeID.IdentifierID
                    && ((IIdentifierNode) farLeft).getName().equals("this"))
            {

            }
            else
            {
                prefix = getEmitter().stringifyNode(farLeft);
            }
        }

        String name = accessor.getBaseName();
        write(prefix + ".set_" + name + "(");
        write(prefix + ".get_" + name + "()");
        write(" ");
        write(node.getOperator().getOperatorText().replace("=", ""));
        write(" ");
        getWalker().walk(right);
        write(")");
    }
}
