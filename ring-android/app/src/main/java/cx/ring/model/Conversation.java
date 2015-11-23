package cx.ring.model;

import android.content.res.Resources;
import android.database.ContentObservable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import cx.ring.R;
import cx.ring.history.HistoryCall;
import cx.ring.history.HistoryEntry;
import cx.ring.history.HistoryText;
import cx.ring.model.account.Account;

public class Conversation extends ContentObservable implements Parcelable
{
    static final String TAG = Conversation.class.getSimpleName();
    private final static Random rand = new Random();

    public CallContact contact;
    //private HistoryEntry history;
    /** accountId -> histroy entries */
    final private Map<String, HistoryEntry> history = new HashMap<>();

    //private Conference current_call = null;
    public final ArrayList<Conference> current_calls;

    // runtime flag set to true if the user
    public boolean mVisible = false;
    public Date lastRead = new Date();
    public int notificationId;

    public String getLastNumberUsed(String accountID) {
        HistoryEntry he = history.get(accountID);
        if (he == null)
            return null;
        return he.getLastNumberUsed();
    }

    public Conference getConference(String id) {
        for (Conference c : current_calls)
            if (c.getId().contentEquals(id) || c.getCallById(id) != null)
                return c;
        return null;
    }

    public void addConference(Conference c) {
        current_calls.add(c);
    }

    public void removeConference(Conference c) {
        current_calls.remove(c);
    }

    public Pair<HistoryEntry, HistoryCall> findHistoryByCallId(String id) {
        for (HistoryEntry e : history.values()) {
            for (HistoryCall c : e.getCalls().values()) {
                if (c.getCallId().equals(id))
                    return new Pair<>(e, c);
            }
        }
        return null;
    }

    public class ConversationElement {
        public HistoryCall call = null;
        public TextMessage text = null;
        public ConversationElement(HistoryCall c) {
            call = c;
        }
        public ConversationElement(TextMessage t) {
            text = t;
        }
        public long getDate() {
            if (text != null)
                return text.getTimestamp();
            else if (call != null)
                return call.call_start;
            return 0;
        }
    }

    public Conversation(CallContact c) {
        contact = c;
        current_calls = new ArrayList<>();
        notificationId = rand.nextInt();
    }

    public static final Creator<Conversation> CREATOR = new Creator<Conversation>() {
        @Override
        public Conversation createFromParcel(Parcel in) {
            return new Conversation(in);
        }

        @Override
        public Conversation[] newArray(int size) {
            return new Conversation[size];
        }
    };

    public CallContact getContact() {
        return contact;
    }

    public Date getLastInteraction() {
        if (!current_calls.isEmpty()) {
            return new Date();
        }
        Date d = new Date(0);

        //for (Map.Entry<String, HistoryEntry> e : history.entrySet()) {
        for (HistoryEntry e : history.values()) {
            Date nd = e.getLastInteraction();
            if (d.compareTo(nd) < 0)
                d = nd;
        }
        return d;
    }

    public String getLastInteractionSumary(Resources resources) {
        if (!current_calls.isEmpty()) {
            return resources.getString(R.string.ongoing_call);
        }
        Pair<Date, String> d = new Pair<>(new Date(0), null);

        for (HistoryEntry e : history.values()) {
            Pair<Date, String> nd = e.getLastInteractionSumary(resources);
            if (nd == null)
                continue;
            if (d.first.compareTo(nd.first) < 0)
                d = nd;
        }
        return d.second;
    }

    public void addHistoryCall(HistoryCall c) {
        String accountId = c.getAccountID();
        if (history.containsKey(accountId))
            history.get(accountId).addHistoryCall(c, contact);
        else {
            HistoryEntry e = new HistoryEntry(accountId, contact);
            e.addHistoryCall(c, contact);
            history.put(accountId, e);
        }
    }
    public void addTextMessage(TextMessage txt) {
        if (txt.getCallId() != null && !txt.getCallId().isEmpty()) {
            Conference conf = getConference(txt.getCallId());
            if (conf == null)
                return;
            conf.addSipMessage(txt);
        }
        if (txt.getContact() == null)
            txt.setContact(contact);
        String accountId = txt.getAccount();
        if (history.containsKey(accountId))
            history.get(accountId).addTextMessage(txt);
        else {
            HistoryEntry e = new HistoryEntry(accountId, contact);
            e.addTextMessage(txt);
            history.put(accountId, e);
        }
    }

    /*public HistoryEntry getHistory(String account_id) {
        return history.get(account_id);
    }*/

    public ArrayList<ConversationElement> getHistory() {
        ArrayList<ConversationElement> all = new ArrayList<>();
        for (HistoryEntry e : history.values()) {
            for (HistoryCall c : e.getCalls().values())
                all.add(new ConversationElement(c));
            for (TextMessage t : e.getTextMessages().values())
                all.add(new ConversationElement(t));
        }
        Collections.sort(all, new Comparator<ConversationElement>() {
            @Override
            public int compare(ConversationElement lhs, ConversationElement rhs) {
                return (int)((lhs.getDate() - rhs.getDate())/1000l);
            }
        });
        return all;
    }

    public void read() {
        lastRead = new Date();
    }

    Date getLastRead() {
        return lastRead;
    }

    public Set<String> getAccountsUsed() {
        return history.keySet();
    }

    public String getLastAccountUsed() {
        String last = null;
        Date d = new Date(0);
        for (Map.Entry<String, HistoryEntry> e : history.entrySet()) {
            Date nd = e.getValue().getLastInteraction();
            if (d.compareTo(nd) < 0) {
                d = nd;
                last = e.getKey();
            }
        }
        return last;
    }

    public Conference getCurrentCall() {
        if (current_calls.isEmpty())
            return null;
        return current_calls.get(0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(contact, flags);
        //dest.writeParcelable(current_call, flags);
        dest.writeList(current_calls);
        dest.writeList(new ArrayList<>(history.values()));
        dest.writeList(new ArrayList<>(history.keySet()));
        dest.writeInt(notificationId);
    }

    protected Conversation(Parcel in) {
        contact = in.readParcelable(CallContact.class.getClassLoader());
        //current_call = in.readParcelable(Conference.class.getClassLoader());
        current_calls = in.readArrayList(Conference.class.getClassLoader());

        ArrayList<HistoryEntry> values = new ArrayList<>();
        in.readList(values, HistoryEntry.class.getClassLoader());
        ArrayList<String> keys = new ArrayList<>();
        in.readList(keys, String.class.getClassLoader());
        for (int i = 0; i < keys.size(); ++i)
            history.put(keys.get(i), values.get(i));

        notificationId = in.readInt();
    }

    public Collection<TextMessage> getTextMessages() {
        return getTextMessages(null);
    }

    public Collection<TextMessage> getTextMessages(Date since) {
        TreeMap<Long, TextMessage> texts = new TreeMap<>();
        for (HistoryEntry h : history.values()) {
            texts.putAll(since == null ? h.getTextMessages() : h.getTextMessages(since.getTime()));
        }
        return texts.values();
    }

    public Collection<TextMessage> getUnreadTextMessages() {
        long since = mVisible ? System.currentTimeMillis() : getLastRead().getTime();
        TreeMap<Long, TextMessage> texts = new TreeMap<>();
        for (HistoryEntry h : history.values()) {
            texts.putAll(h.getTextMessages(since));
        }
        return texts.values();
    }

}
