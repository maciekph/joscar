/*
 *  Copyright (c) 2004, The Joust Project
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without 
 *  modification, are permitted provided that the following conditions 
 *  are met:
 *
 *  - Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer. 
 *  - Redistributions in binary form must reproduce the above copyright 
 *    notice, this list of conditions and the following disclaimer in 
 *    the documentation and/or other materials provided with the 
 *    distribution. 
 *  - Neither the name of the Joust Project nor the names of its 
 *    contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission. 
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS 
 *  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 *  COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
 *  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT 
 *  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN 
 *  ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 *  POSSIBILITY OF SUCH DAMAGE.
 *
 *  File created by keith @ Feb 6, 2004
 *
 */

package net.kano.joustsim.oscar.oscar.service.ssi;

import net.kano.joscar.MiscTools;
import net.kano.joscar.ssiitem.SsiItemObjectFactory;
import net.kano.joscar.ssiitem.DefaultSsiItemObjFactory;
import net.kano.joscar.ssiitem.SsiItemObj;
import net.kano.joscar.ssiitem.GroupItem;
import net.kano.joscar.ssiitem.RootItem;
import net.kano.joscar.flapcmd.SnacCommand;
import net.kano.joscar.snac.SnacPacketEvent;
import net.kano.joscar.snac.SnacRequestListener;
import net.kano.joscar.snac.SnacResponseEvent;
import net.kano.joscar.snac.SnacResponseListener;
import net.kano.joscar.snaccmd.conn.SnacFamilyInfo;
import net.kano.joscar.snaccmd.ssi.ActivateSsiCmd;
import net.kano.joscar.snaccmd.ssi.CreateItemsCmd;
import net.kano.joscar.snaccmd.ssi.DeleteItemsCmd;
import net.kano.joscar.snaccmd.ssi.ItemsCmd;
import net.kano.joscar.snaccmd.ssi.ModifyItemsCmd;
import net.kano.joscar.snaccmd.ssi.SsiCommand;
import net.kano.joscar.snaccmd.ssi.SsiDataCmd;
import net.kano.joscar.snaccmd.ssi.SsiDataModResponse;
import net.kano.joscar.snaccmd.ssi.SsiDataRequest;
import net.kano.joscar.snaccmd.ssi.SsiItem;
import net.kano.joscar.snaccmd.ssi.SsiRightsRequest;
import net.kano.joustsim.oscar.AimConnection;
import net.kano.joustsim.oscar.oscar.OscarConnection;
import net.kano.joustsim.oscar.oscar.service.Service;
import net.kano.joustsim.oscar.oscar.service.ServiceEvent;
import net.kano.joustsim.oscar.oscar.service.bos.ServerReadyEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.ArrayList;

public class SsiService extends Service {
    private SsiBuddyList buddyList = new SsiBuddyList(this);
    private boolean requestedList = false;
    private static final int NUM_IDS = 0x7fff+1;

    public SsiService(AimConnection aimConnection,
            OscarConnection oscarConnection) {
        super(aimConnection, oscarConnection, SsiCommand.FAMILY_SSI);

    }

    public SnacFamilyInfo getSnacFamilyInfo() {
        return SsiCommand.FAMILY_INFO;
    }

    public void connected() {
        getOscarConnection().getSnacProcessor().addGlobalResponseListener(new SnacResponseListener() {
            public void handleResponse(SnacResponseEvent e) {
                if (e.getSnacCommand() instanceof SsiDataModResponse) {
                    SsiDataModResponse dataModResponse = (SsiDataModResponse) e.getSnacCommand();
                    SnacCommand origCmd = e.getRequest().getCommand();
                    boolean create = origCmd instanceof CreateItemsCmd;
                    boolean modify = origCmd instanceof ModifyItemsCmd;
                    boolean delete = origCmd instanceof DeleteItemsCmd;
                    if (!(create || modify || delete)) {
                        return;
                    }
                    ItemsCmd itemsCmd = (ItemsCmd) origCmd;
                    List<SsiItem> items = itemsCmd.getItems();

                    int[] results = dataModResponse.getResults();
                    for (int i = 0; i < results.length; i++) {
                        int result = results[i];

                        SsiItem item = items.get(i);
                        if (result == SsiDataModResponse.RESULT_SUCCESS) {
                            if (create) {
                                itemCreated(item);
                            } else if (modify) {
                                itemModified(item);
                            } else if (delete) itemDeleted(item);
                        } else if (result == SsiDataModResponse.RESULT_ID_TAKEN) {
                            int id = item.getId();
                            Set<Integer> possible = new TreeSet<Integer>(
                                    getIdsForType(item.getItemType()));
                            System.out.println("ID taken: " + id);
                            System.out.println("possible ID's: " + possible);
                        }
                    }
                }
            }
        });
        if (!getOscarConnection().getServiceEvents(ServerReadyEvent.class).isEmpty()) {
            requestList();
        }
    }

    public void handleEvent(ServiceEvent event) {
        super.handleEvent(event);
        if (event instanceof ServerReadyEvent) requestList();
    }

    private void requestList() {
        synchronized(this) {
            if (requestedList) return;
            requestedList = true;
        }
        sendSnac(new SsiRightsRequest());
        sendSnac(new SsiDataRequest());
    }

    public void handleSnacPacket(SnacPacketEvent snacPacketEvent) {
        SnacCommand snac = snacPacketEvent.getSnacCommand();

        System.out.println("got ssi snac in " + MiscTools.getClassName(this)
                + ": " + snac);
        if (snac == null) {
            System.out.println("- packet: " + snacPacketEvent.getSnacPacket());
        }
        final List<Exception> exceptions = new ArrayList<Exception>();
        if (snac instanceof SsiDataCmd) {
            SsiDataCmd ssiDataCmd = (SsiDataCmd) snac;
            for (SsiItem item : ssiDataCmd.getItems()) {
                try {
                    itemCreated(item);
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }

            if ((snac.getFlag2() & SnacCommand.SNACFLAG2_MORECOMING) == 0) {
                sendSnac(new ActivateSsiCmd());
                setReady();
            }
        } else if (snac instanceof CreateItemsCmd) {
            CreateItemsCmd createItemsCmd = (CreateItemsCmd) snac;
            for (SsiItem ssiItem : createItemsCmd.getItems()) {
                try {
                    itemCreated(ssiItem);
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
        } else if (snac instanceof ModifyItemsCmd) {
            ModifyItemsCmd modifyItemsCmd = (ModifyItemsCmd) snac;
            for (SsiItem ssiItem : modifyItemsCmd.getItems()) {
                try {
                    itemModified(ssiItem);
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
        } else if (snac instanceof DeleteItemsCmd) {
            DeleteItemsCmd deleteItemsCmd = (DeleteItemsCmd) snac;
            for (SsiItem ssiItem : deleteItemsCmd.getItems()) {
                try {
                    itemDeleted(ssiItem);
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
        }
        if (exceptions.size() == 1) {
            Exception exception = exceptions.get(0);
            if (exception instanceof RuntimeException) {
                throw (RuntimeException) exception;
            } else {
                throw new IllegalStateException(exception);
            }
        } else if (!exceptions.isEmpty()) {
            throw new MultipleExceptionsException(exceptions);
        }
    }

    public MutableBuddyList getBuddyList() {
        return buddyList;
    }

    private Map<ItemId, SsiItem> items = new HashMap<ItemId, SsiItem>();

    public void itemCreated(SsiItem item) {
        ItemId id = new ItemId(item);
        synchronized(this) {
            SsiItem old = items.get(id);
            if (old != null) {
                throw new IllegalArgumentException("item " + id + " already exists "
                        + "as " + old + ", tried to add as " + item);
            }
            items.put(id, item);
        }
        buddyList.handleItemCreated(item);
    }

    public void itemModified(SsiItem item) {
        ItemId id = new ItemId(item);
        synchronized (this) {
            SsiItem oldItem = items.get(id);
            if (oldItem == null) {
                throw new IllegalArgumentException("item does not exist: " + id
                        + " - " + item);
            }
            items.put(id, item);
        }
        buddyList.handleItemModified(item);
    }

    public void itemDeleted(SsiItem item) {
        SsiItem removed;
        synchronized (this) {
            removed = items.remove(new ItemId(item));
        }
        if (removed == null) {
            throw new IllegalArgumentException("no such item " + item);
        }
        buddyList.handleItemDeleted(item);
    }

    private Random random = new Random();

    synchronized int getUniqueItemId(int type, int parent) {
        if (type == SsiItem.TYPE_GROUP) {
            throw new IllegalArgumentException("groups all have id 0");
        }
        Set<Integer> idsForType = getIdsForType(type);
        if (type == SsiItem.TYPE_BUDDY) {
            addUsedBuddyIdsInGroup(idsForType, parent);
        }
        int nextid;
        do {
            nextid = random.nextInt(NUM_IDS);
        } while (idsForType.contains(nextid));
        return nextid;
    }

    private synchronized void addUsedBuddyIdsInGroup(Set<Integer> idsForType,
            int parent) {
        for (Map.Entry<ItemId,SsiItem> id : items.entrySet()) {
            ItemId key = id.getKey();
            if (key.getType() == SsiItem.TYPE_GROUP
                    && key.getParent() == parent) {
                SsiItemObjectFactory objFactory = new DefaultSsiItemObjFactory();
                SsiItemObj itemObj = objFactory.getItemObj(id.getValue());
                if (itemObj instanceof GroupItem) {
                    GroupItem groupItem = (GroupItem) itemObj;
                    for (int bid : groupItem.getBuddies()) {
                        idsForType.add(bid);
                    }
                }
            }
        }
    }

    private synchronized void addUsedGroupIdsInRoot(Set<Integer> idsForType) {
        for (Map.Entry<ItemId,SsiItem> id : items.entrySet()) {
            ItemId key = id.getKey();
            if (key.getType() == SsiItem.TYPE_GROUP
                    && key.getParent() == 0) {
                SsiItemObjectFactory objFactory = new DefaultSsiItemObjFactory();
                SsiItemObj itemObj = objFactory.getItemObj(id.getValue());
                if (itemObj instanceof RootItem) {
                    RootItem groupItem = (RootItem) itemObj;
                    for (int bid : groupItem.getGroupids()) {
                        idsForType.add(bid);
                    }
                }
            }
        }
    }

    private synchronized Set<Integer> getIdsForType(int type) {
        Set<Integer> idsForType = new HashSet<Integer>(items.size());
        for (ItemId id : items.keySet()) {
            if (id.getType() == type) idsForType.add(id.getId());
        }
        return idsForType;
    }
    private synchronized Set<Integer> getPossiblyUsedGroupIds() {
        Set<Integer> idsForType = new HashSet<Integer>(items.size());
        for (ItemId id : items.keySet()) {
            if (id.getType() == SsiItem.TYPE_GROUP) idsForType.add(id.getParent());
        }
        return idsForType;
    }

    //TODO: test new unique group id and buddy id methodo
    synchronized int getUniqueGroupId() {
        Set<Integer> groupIds = getPossiblyUsedGroupIds();
        addUsedGroupIdsInRoot(groupIds);
        int nextid;
        do {
            nextid = random.nextInt(NUM_IDS);
        } while (groupIds.contains(nextid));
        return nextid;
    }

    public void sendSsiModification(ItemsCmd cmd,
            SnacRequestListener listener) {
        sendSnacRequest(cmd, listener);
    }

    public void sendSsiModification(ItemsCmd cmd) {
        sendSnac(cmd);
    }

    private static class ItemId {
        private final int type;
        private final int parent;
        private final int id;

        public ItemId(int type, int parent, int id) {
            this.type = type;
            this.parent = parent;
            this.id = id;
        }

        public ItemId(SsiItem item) {
            this(item.getItemType(), item.getParentId(), item.getId());
        }

        public int getType() {
            return type;
        }

        public int getParent() {
            return parent;
        }

        public int getId() {
            return id;
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final ItemId itemId = (ItemId) o;

            if (id != itemId.id) return false;
            if (parent != itemId.parent) return false;
            if (type != itemId.type) return false;

            return true;
        }

        public int hashCode() {
            int result;
            result = type;
            result = 29 * result + parent;
            result = 29 * result + id;
            return result;
        }

        public String toString() {
            return "ItemId{" +
                    "type=" + MiscTools.findIntField(SsiItem.class, type, "TYPE_.*") +
                    ", parent=0x" + Integer.toHexString(parent) +
                    ", id=0x" + Integer.toHexString(id) +
                    "}";
        }
    }

}
