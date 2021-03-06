/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.messaging;

import static org.jboss.as.controller.registry.AttributeAccess.Flag.RESTART_NONE;
import static org.jboss.dmr.ModelType.BOOLEAN;
import static org.jboss.dmr.ModelType.STRING;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.hornetq.core.security.Role;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;

/**
 * Security role resource definition
 *
 * @author <a href="http://jmesnil.net">Jeff Mesnil</a> (c) 2012 Red Hat Inc.
 */
public class SecurityRoleDefinition extends SimpleResourceDefinition {

    public static ObjectTypeAttributeDefinition getObjectTypeAttributeDefinition() {
        // add the role name as an attribute of the object type
        SimpleAttributeDefinition[] attrs = new SimpleAttributeDefinition[ATTRIBUTES.length + 1];
        attrs[0] = NAME;
        System.arraycopy(ATTRIBUTES, 0, attrs, 1, ATTRIBUTES.length);
        return ObjectTypeAttributeDefinition.Builder.of(CommonAttributes.ROLE, attrs).build();
    }

    private static SimpleAttributeDefinition create(final String name, final String xmlName) {
        return SimpleAttributeDefinitionBuilder.create(name, BOOLEAN)
                .setXmlName(xmlName)
                .setFlags(RESTART_NONE)
                .build();
    }

    static final SimpleAttributeDefinition SEND = create("send", "send");
    static final SimpleAttributeDefinition CONSUME = create("consume", "consume");
    static final SimpleAttributeDefinition CREATE_DURABLE_QUEUE = create("create-durable-queue", "createDurableQueue");
    static final SimpleAttributeDefinition DELETE_DURABLE_QUEUE = create("delete-durable-queue", "deleteDurableQueue");
    static final SimpleAttributeDefinition CREATE_NON_DURABLE_QUEUE = create("create-non-durable-queue", "createNonDurableQueue");
    static final SimpleAttributeDefinition DELETE_NON_DURABLE_QUEUE = create("delete-non-durable-queue", "deleteNonDurableQueue");
    static final SimpleAttributeDefinition MANAGE = create("manage", "manage");

    static final SimpleAttributeDefinition[] ATTRIBUTES = {
        SEND,
        CONSUME,
        CREATE_DURABLE_QUEUE,
        DELETE_DURABLE_QUEUE,
        CREATE_NON_DURABLE_QUEUE,
        DELETE_NON_DURABLE_QUEUE,
        MANAGE
    };

    static final SimpleAttributeDefinition NAME = SimpleAttributeDefinitionBuilder.create("name", STRING)
            .build();

    static final Map<String, AttributeDefinition> ROLE_ATTRIBUTES_BY_XML_NAME;
    private final boolean registerRuntimeOnly;
    private final boolean readOnly;

    static {
        Map<String, AttributeDefinition> robxn = new HashMap<String, AttributeDefinition>();
        for (AttributeDefinition attr : SecurityRoleDefinition.ATTRIBUTES) {
            robxn.put(attr.getXmlName(), attr);
        }
        // Legacy xml names
        robxn.put("createTempQueue", SecurityRoleDefinition.CREATE_NON_DURABLE_QUEUE);
        robxn.put("deleteTempQueue", SecurityRoleDefinition.DELETE_NON_DURABLE_QUEUE);
        ROLE_ATTRIBUTES_BY_XML_NAME = Collections.unmodifiableMap(robxn);
    }

    static Role transform(final OperationContext context, final String name, final ModelNode node) throws OperationFailedException {
        final boolean send = SEND.resolveModelAttribute(context, node).asBoolean();
        final boolean consume = CONSUME.resolveModelAttribute(context, node).asBoolean();
        final boolean createDurableQueue = CREATE_DURABLE_QUEUE.resolveModelAttribute(context, node).asBoolean();
        final boolean deleteDurableQueue = DELETE_DURABLE_QUEUE.resolveModelAttribute(context, node).asBoolean();
        final boolean createNonDurableQueue = CREATE_NON_DURABLE_QUEUE.resolveModelAttribute(context, node).asBoolean();
        final boolean deleteNonDurableQueue = DELETE_NON_DURABLE_QUEUE.resolveModelAttribute(context, node).asBoolean();
        final boolean manage = MANAGE.resolveModelAttribute(context, node).asBoolean();
        return new Role(name, send, consume, createDurableQueue, deleteDurableQueue, createNonDurableQueue, deleteNonDurableQueue, manage);
    }

    public static SecurityRoleDefinition newReadOnlySecurityRoleDefinition() {
        return new SecurityRoleDefinition(true, true, null, null);
    }

    public static SecurityRoleDefinition newSecurityRoleDefinition(final boolean registerRuntimeOnly) {
        return new SecurityRoleDefinition(registerRuntimeOnly, false, SecurityRoleAdd.INSTANCE, SecurityRoleRemove.INSTANCE);
    }

    private SecurityRoleDefinition(final boolean registerRuntimeOnly, final boolean readOnly, final OperationStepHandler addHandler, final OperationStepHandler removeHandler) {
        super(PathElement.pathElement(CommonAttributes.ROLE),
                MessagingExtension.getResourceDescriptionResolver(CommonAttributes.SECURITY_ROLE),
                addHandler,
                removeHandler);
        this.registerRuntimeOnly = registerRuntimeOnly;
        this.readOnly = readOnly;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registry) {
        super.registerAttributes(registry);

        if (readOnly) {
            for (SimpleAttributeDefinition attr : ATTRIBUTES) {
                AttributeDefinition readOnlyAttr = SimpleAttributeDefinitionBuilder.create(attr)
                        .setStorageRuntime()
                        .build();
                registry.registerReadOnlyAttribute(readOnlyAttr, SecurityRoleReadAttributeHandler.INSTANCE);
            }
        } else {
            for (AttributeDefinition attr : ATTRIBUTES) {
                if (registerRuntimeOnly || !attr.getFlags().contains(AttributeAccess.Flag.STORAGE_RUNTIME)) {
                    registry.registerReadWriteAttribute(attr, null, SecurityRoleAttributeHandler.INSTANCE);
                }
            }
        }
    }
}