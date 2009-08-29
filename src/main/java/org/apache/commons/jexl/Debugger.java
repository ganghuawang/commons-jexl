/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.jexl;

import org.apache.commons.jexl.parser.ASTAdditiveNode;
import org.apache.commons.jexl.parser.ASTAdditiveOperator;
import org.apache.commons.jexl.parser.ASTAndNode;
import org.apache.commons.jexl.parser.ASTArrayAccess;
import org.apache.commons.jexl.parser.ASTArrayLiteral;
import org.apache.commons.jexl.parser.ASTAssignment;
import org.apache.commons.jexl.parser.ASTBitwiseAndNode;
import org.apache.commons.jexl.parser.ASTBitwiseComplNode;
import org.apache.commons.jexl.parser.ASTBitwiseOrNode;
import org.apache.commons.jexl.parser.ASTBitwiseXorNode;
import org.apache.commons.jexl.parser.ASTBlock;
import org.apache.commons.jexl.parser.ASTConstructorNode;
import org.apache.commons.jexl.parser.ASTDivNode;
import org.apache.commons.jexl.parser.ASTEQNode;
import org.apache.commons.jexl.parser.ASTEmptyFunction;
import org.apache.commons.jexl.parser.ASTFalseNode;
import org.apache.commons.jexl.parser.ASTFloatLiteral;
import org.apache.commons.jexl.parser.ASTForeachStatement;
import org.apache.commons.jexl.parser.ASTFunctionNode;
import org.apache.commons.jexl.parser.ASTGENode;
import org.apache.commons.jexl.parser.ASTGTNode;
import org.apache.commons.jexl.parser.ASTIdentifier;
import org.apache.commons.jexl.parser.ASTIfStatement;
import org.apache.commons.jexl.parser.ASTIntegerLiteral;
import org.apache.commons.jexl.parser.ASTJexlScript;
import org.apache.commons.jexl.parser.ASTLENode;
import org.apache.commons.jexl.parser.ASTLTNode;
import org.apache.commons.jexl.parser.ASTMapEntry;
import org.apache.commons.jexl.parser.ASTMapLiteral;
import org.apache.commons.jexl.parser.ASTMethodNode;
import org.apache.commons.jexl.parser.ASTModNode;
import org.apache.commons.jexl.parser.ASTMulNode;
import org.apache.commons.jexl.parser.ASTNENode;
import org.apache.commons.jexl.parser.ASTNotNode;
import org.apache.commons.jexl.parser.ASTNullLiteral;
import org.apache.commons.jexl.parser.ASTOrNode;
import org.apache.commons.jexl.parser.ASTReference;
import org.apache.commons.jexl.parser.ASTSizeFunction;
import org.apache.commons.jexl.parser.ASTSizeMethod;
import org.apache.commons.jexl.parser.ASTStringLiteral;
import org.apache.commons.jexl.parser.ASTTernaryNode;
import org.apache.commons.jexl.parser.ASTTrueNode;
import org.apache.commons.jexl.parser.ASTUnaryMinusNode;
import org.apache.commons.jexl.parser.ASTWhileStatement;
import org.apache.commons.jexl.parser.JexlNode;
import org.apache.commons.jexl.parser.SimpleNode;

import org.apache.commons.jexl.parser.ParserVisitor;

/**
 * Helps pinpoint the cause of problems in expressions that fail during evaluation.
 * It rebuilds an expression string from the tree and the start/end offsets of the cause
 * in that string.
 * This implies that exceptions during evaluation should allways carry the node that's causing
 * the error.
 */
final class Debugger implements ParserVisitor {
    /** The builder to compose messages. */
    private StringBuilder builder;
    /** The cause of the issue to debug. */
    private JexlNode cause;
    /** The starting character location offset of the cause in the builder. */
    private int start;
    /** The ending character location offset of the cause in the builder. */
    private int end;

    /**
     * Creates a Debugger.
     */
    Debugger() {
        builder = new StringBuilder();
        cause = null;
        start = 0;
        end = 0;
    }

    /**
     * Seeks the location of an error cause (a node) in an expression.
     * @param node the node to debug
     * @return true if the cause was located, false otherwise
     */
    public boolean debug(JexlNode node) {
        start = 0;
        end = 0;
        if (node != null) {
            builder = new StringBuilder();
            this.cause = node;
            // make arg cause become the root cause
            JexlNode root = node;
            while (root.jjtGetParent() != null) {
                root = root.jjtGetParent();
            }
            root.jjtAccept(this, null);
        }
        return end > 0;
    }

    /**
     * @return The rebuilt expression
     */
    public String data() {
        return builder.toString();
    }

    /**
     * @return The starting offset location of the cause in the expression
     */
    public int start() {
        return start;
    }

    /**
     * @return The end offset location of the cause in the expression
     */
    public int end() {
        return end;
    }

    /**
     * Checks if a child node is the cause to debug &amp; adds its representation
     * to the rebuilt expression.
     * @param node the child node
     * @param data visitor pattern argument
     * @return visitor pattern value
     */
    private Object accept(JexlNode node, Object data) {
        if (node == cause) {
            start = builder.length();
        }
        Object value = node.jjtAccept(this, data);
        if (node == cause) {
            end = builder.length();
        }
        return value;
    }

    /**
     * Adds a statement node to the rebuilt expression.
     * @param child the child node
     * @param data visitor pattern argument
     * @return visitor pattern value
     */
    private Object acceptStatement(JexlNode child, Object data) {
        Object value = accept(child, data);
        // blocks, if, for & while dont need a ';' at end
        if (child instanceof ASTBlock
            || child instanceof ASTIfStatement
            || child instanceof ASTForeachStatement
            || child instanceof ASTWhileStatement) {
            return value;
        }
        builder.append(";");
        return value;
    }

    /**
     * Checks if a terminal node is the the cause to debug &amp; adds its
     * representation to the rebuilt expression.
     * @param node the child node
     * @param image the child node token image (may be null)
     * @param data visitor pattern argument
     * @return visitor pattern value
     */
    private Object check(JexlNode node, String image, Object data) {
        if (node == cause) {
            start = builder.length();
        }
        if (image != null) {
            builder.append(image);
        } else {
            builder.append(node.toString());
        }
        if (node == cause) {
            end = builder.length();
        }
        return data;
    }

    /**
     * Checks if the children of a node using infix notation is the cause to debug,
     * adds their representation to the rebuilt expression.
     * @param node the child node
     * @param infix the child node token
     * @param paren whether the child should be parenthesized
     * @param data visitor pattern argument
     * @return visitor pattern value
     */
    private Object infixChildren(JexlNode node, String infix, boolean paren, Object data) {
        int num = node.jjtGetNumChildren(); //child.jjtGetNumChildren() > 1;
        if (paren) {
            builder.append("(");
        }
        for (int i = 0; i < num; ++i) {
            if (i > 0) {
                builder.append(infix);
            }
            accept(node.jjtGetChild(i), data);
        }
        if (paren) {
            builder.append(")");
        }
        return data;
    }

    /**
     * Checks if the child of a node using prefix notation is the cause to debug,
     * adds their representation to the rebuilt expression.
     * @param node the node
     * @param prefix the node token
     * @param data visitor pattern argument
     * @return visitor pattern value
     */
    private Object prefixChild(JexlNode node, String prefix, Object data) {
        boolean paren = node.jjtGetChild(0).jjtGetNumChildren() > 1;
        builder.append(prefix);
        if (paren) {
            builder.append("(");
        }
        accept(node.jjtGetChild(0), data);
        if (paren) {
            builder.append(")");
        }
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(ASTAdditiveNode node, Object data) {
        // need parenthesis if not in operator precedence order
        boolean paren = node.jjtGetParent() instanceof ASTMulNode
                        || node.jjtGetParent() instanceof ASTDivNode
                        || node.jjtGetParent() instanceof ASTModNode;
        int num = node.jjtGetNumChildren(); //child.jjtGetNumChildren() > 1;
        if (paren) {
            builder.append("(");
        }
        accept(node.jjtGetChild(0), data);
        for (int i = 1; i < num; ++i) {
            accept(node.jjtGetChild(i), data);
        }
        if (paren) {
            builder.append(")");
        }
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(ASTAdditiveOperator node, Object data) {
        builder.append(' ');
        builder.append(node.image);
        builder.append(' ');
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(ASTAndNode node, Object data) {
        return infixChildren(node, " && ", false, data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTArrayAccess node, Object data) {
        accept(node.jjtGetChild(0), data);
        int num = node.jjtGetNumChildren();
        for (int i = 1; i < num; ++i) {
            builder.append("[");
            accept(node.jjtGetChild(i), data);
            builder.append("]");
        }
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(ASTArrayLiteral node, Object data) {
        int num = node.jjtGetNumChildren();
        builder.append("[ ");
        accept(node.jjtGetChild(0), data);
        for (int i = 1; i < num; ++i) {
            builder.append(", ");
            accept(node.jjtGetChild(i), data);
        }
        builder.append(" ]");
        return data;
    }
    
    /** {@inheritDoc} */
    public Object visit(ASTAssignment node, Object data) {
        return infixChildren(node, " = ", false, data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTBitwiseAndNode node, Object data) {
        return infixChildren(node, " & ", false, data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTBitwiseComplNode node, Object data) {
        return prefixChild(node, "~", data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTBitwiseOrNode node, Object data) {
        boolean paren = node.jjtGetParent() instanceof ASTBitwiseAndNode;
        return infixChildren(node, " | ", paren, data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTBitwiseXorNode node, Object data) {
        boolean paren = node.jjtGetParent() instanceof ASTBitwiseAndNode;
        return infixChildren(node, " ^ ", paren, data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTBlock node, Object data) {
        builder.append("{ ");
        int num = node.jjtGetNumChildren();
        for (int i = 0; i < num; ++i) {
            JexlNode child = node.jjtGetChild(i);
            acceptStatement(child, data);
        }
        builder.append(" }");
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(ASTDivNode node, Object data) {
        return infixChildren(node, " / ", false, data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTEmptyFunction node, Object data) {
        builder.append("empty(");
        accept(node.jjtGetChild(0), data);
        builder.append(")");
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(ASTEQNode node, Object data) {
        return infixChildren(node, " == ", false, data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTFalseNode node, Object data) {
        return check(node, "false", data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTFloatLiteral node, Object data) {
        return check(node, node.image, data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTForeachStatement node, Object data) {
        builder.append("for(");
        accept(node.jjtGetChild(0), data);
        builder.append(" : ");
        accept(node.jjtGetChild(1), data);
        builder.append(") ");
        if (node.jjtGetNumChildren() > 2) {
            acceptStatement(node.jjtGetChild(2), data);
        } else {
            builder.append(';');
        }
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(ASTGENode node, Object data) {
        return infixChildren(node, " >= ", false, data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTGTNode node, Object data) {
        return infixChildren(node, " > ", false, data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTIdentifier node, Object data) {
        return check(node, node.image, data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTIfStatement node, Object data) {
        builder.append("if (");
        accept(node.jjtGetChild(0), data);
        builder.append(") ");
        if (node.jjtGetNumChildren() > 1) {
            acceptStatement(node.jjtGetChild(1), data);
            if (node.jjtGetNumChildren() > 2) {
                builder.append(" else ");
                acceptStatement(node.jjtGetChild(2), data);
            } else {
                builder.append(';');
            }
        } else {
            builder.append(';');
        }
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(ASTIntegerLiteral node, Object data) {
        return check(node, node.image, data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTJexlScript node, Object data) {
        int num = node.jjtGetNumChildren();
        for (int i = 0; i < num; ++i) {
            JexlNode child = node.jjtGetChild(i);
            acceptStatement(child, data);
        }
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(ASTLENode node, Object data) {
        return infixChildren(node, " <= ", false, data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTLTNode node, Object data) {
        return infixChildren(node, " < ", false, data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTMapEntry node, Object data) {
        accept(node.jjtGetChild(0), data);
        builder.append(" : ");
        accept(node.jjtGetChild(1), data);
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(ASTMapLiteral node, Object data) {
        int num = node.jjtGetNumChildren();
        builder.append("{ ");
        accept(node.jjtGetChild(0), data);
        for (int i = 1; i < num; ++i) {
            builder.append(", ");
            accept(node.jjtGetChild(i), data);
        }
        builder.append(" }");
        return data;
    }

    /** {@inheritDoc} */
     public Object visit(ASTConstructorNode node, Object data) {
        int num = node.jjtGetNumChildren();
        builder.append("new ");
        builder.append("(");
        accept(node.jjtGetChild(0), data);
        for (int i = 1; i < num; ++i) {
            builder.append(", ");
            accept(node.jjtGetChild(i), data);
        }
        builder.append(")");
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(ASTFunctionNode node, Object data) {
        int num = node.jjtGetNumChildren();
        accept(node.jjtGetChild(0), data);
        builder.append(":");
        accept(node.jjtGetChild(1), data);
        builder.append("(");
        for (int i = 2; i < num; ++i) {
            if (i > 2) {
                builder.append(", ");
            }
            accept(node.jjtGetChild(i), data);
        }
        builder.append(")");
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(ASTMethodNode node, Object data) {
        int num = node.jjtGetNumChildren();
        accept(node.jjtGetChild(0), data);
        builder.append("(");
        for (int i = 1; i < num; ++i) {
            if (i > 1) {
                builder.append(", ");
            }
            accept(node.jjtGetChild(i), data);
        }
        builder.append(")");
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(ASTModNode node, Object data) {
        return infixChildren(node, " % ", false, data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTMulNode node, Object data) {
        return infixChildren(node, " * ", false, data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTNENode node, Object data) {
        return infixChildren(node, " != ", false, data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTNotNode node, Object data) {
        builder.append("!");
        accept(node.jjtGetChild(0), data);
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(ASTNullLiteral node, Object data) {
        check(node, "null", data);
        return data;
    }

    /** {@inheritDoc} */
   public Object visit(ASTOrNode node, Object data) {
        // need parenthesis if not in operator precedence order
        boolean paren = node.jjtGetParent() instanceof ASTAndNode;
        return infixChildren(node, " || ", paren, data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTReference node, Object data) {
        int num = node.jjtGetNumChildren();
        accept(node.jjtGetChild(0), data);
        for (int i = 1; i < num; ++i) {
            builder.append(".");
            accept(node.jjtGetChild(i), data);
        }
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(ASTSizeFunction node, Object data) {
        builder.append("size(");
        accept(node.jjtGetChild(0), data);
        builder.append(")");
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(ASTSizeMethod node, Object data) {
        check(node, "size()", data);
        return data;
    }
    
    /** {@inheritDoc} */
    public Object visit(ASTStringLiteral node, Object data) {
        String img = node.image.replace("'", "\\'");
        return check(node, "'" + img + "'", data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTTernaryNode node, Object data) {
        accept(node.jjtGetChild(0), data);
        if (node.jjtGetNumChildren() > 2) {
            builder.append("? ");
            accept(node.jjtGetChild(1), data);
            builder.append(" : ");
            accept(node.jjtGetChild(2), data);
        } else {
            builder.append("?:");
            accept(node.jjtGetChild(1), data);

        }
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(ASTTrueNode node, Object data) {
        check(node, "true", data);
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(ASTUnaryMinusNode node, Object data) {
        return prefixChild(node, "-", data);
    }

    /** {@inheritDoc} */
    public Object visit(ASTWhileStatement node, Object data) {
        builder.append("while (");
        accept(node.jjtGetChild(0), data);
        builder.append(") ");
        if (node.jjtGetNumChildren() > 1) {
            acceptStatement(node.jjtGetChild(1), data);
        } else {
            builder.append(';');
        }
        return data;
    }

    /** {@inheritDoc} */
    public Object visit(SimpleNode node, Object data) {
        throw new UnsupportedOperationException("unexpected type of node");
    }
}