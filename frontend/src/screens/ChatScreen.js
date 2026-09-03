import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, FlatList, StyleSheet,
  KeyboardAvoidingView, Platform, ActivityIndicator,
} from 'react-native';
import { useAuth } from '../context/AuthContext';
import { fetchMessages, sendMessage } from '../api/conversations';
import { connectSocket, getSocket } from '../socket';
import { Colors } from '../theme';

export default function ChatScreen({ route, navigation }) {
  const { conversation } = route.params;
  const { user } = useAuth();
  const [messages, setMessages] = useState([]);
  const [text, setText] = useState('');
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [typing, setTyping] = useState('');
  const flatList = useRef(null);
  const typingTimer = useRef(null);

  const loadMessages = async () => {
    try {
      const data = await fetchMessages(conversation.id);
      setMessages(data.messages || []);
    } catch (e) {
      console.warn('Load messages error:', e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadMessages();
  }, []);

  // Socket: слушаем новые сообщения и печатание
  useEffect(() => {
    const socket = connectSocket();
    const handler = (msg) => {
      if (msg.conversationId === conversation.id) {
        setMessages((prev) => [...prev, msg]);
      }
    };
    const typingHandler = ({ userId, conversationId: cid }) => {
      if (cid === conversation.id && userId !== user?.id) {
        const peer = conversation.participants?.find((p) => p.user.id === userId)?.user?.username;
        setTyping(peer ? `${peer} печатает...` : '');
        clearTimeout(typingTimer.current);
        typingTimer.current = setTimeout(() => setTyping(''), 3000);
      }
    };
    const stopTypingHandler = ({ conversationId: cid }) => {
      if (cid === conversation.id) setTyping('');
    };

    socket.on('message:new', handler);
    socket.on('typing:start', typingHandler);
    socket.on('typing:stop', stopTypingHandler);
    return () => {
      socket.off('message:new', handler);
      socket.off('typing:start', typingHandler);
      socket.off('typing:stop', stopTypingHandler);
    };
  }, [conversation.id, user?.id]);

  const handleSend = async () => {
    if (!text.trim() || sending) return;
    setSending(true);
    const content = text.trim();
    setText('');
    try {
      const msg = await sendMessage(conversation.id, content);
      // socket уже разошлёт message:new, но для себя добавим
      setMessages((prev) => [...prev, msg]);
    } catch (e) {
      console.warn('Send error:', e.message);
    } finally {
      setSending(false);
    }
  };

  const handleTyping = (value) => {
    setText(value);
    const socket = getSocket();
    if (socket) {
      socket.emit('typing:start', conversation.id);
      clearTimeout(typingTimer.current);
      typingTimer.current = setTimeout(() => {
        socket.emit('typing:stop', conversation.id);
      }, 1500);
    }
  };

  const renderMessage = ({ item }) => {
    const isMe = item.sender?.id === user?.id;
    return (
      <View style={[styles.msgRow, isMe ? styles.msgRowMe : styles.msgRowOther]}>
        <View style={[styles.msgBubble, isMe ? styles.msgBubbleMe : styles.msgBubbleOther]}>
          <Text style={styles.msgText}>{item.content}</Text>
          <Text style={styles.msgTime}>
            {new Date(item.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          </Text>
        </View>
      </View>
    );
  };

  const peerName = conversation.name ||
    conversation.participants?.find((p) => p.user.id !== user?.id)?.user?.username || 'Чат';

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 90 : 0}
    >
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Text style={styles.backBtn}>←</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle} numberOfLines={1}>{peerName}</Text>
        <View style={{ width: 40 }} />
      </View>

      {loading ? (
        <ActivityIndicator size="large" color={Colors.primary} style={{ marginTop: 40 }} />
      ) : (
        <FlatList
          ref={flatList}
          data={messages}
          keyExtractor={(item) => item.id}
          renderItem={renderMessage}
          contentContainerStyle={styles.messagesList}
          onContentSizeChange={() => flatList.current?.scrollToEnd({ animated: false })}
          ListEmptyComponent={
            <Text style={styles.emptyText}>Нет сообщений. Напишите первым!</Text>
          }
        />
      )}

      {typing ? <Text style={styles.typingIndicator}>{typing}</Text> : null}

      <View style={styles.inputRow}>
        <TextInput
          style={styles.input}
          placeholder="Сообщение..."
          placeholderTextColor={Colors.textSecondary}
          value={text}
          onChangeText={handleTyping}
          multiline
          maxLength={2000}
        />
        <TouchableOpacity
          style={[styles.sendBtn, !text.trim() && styles.sendBtnDisabled]}
          onPress={handleSend}
          disabled={!text.trim() || sending}
        >
          {sending ? (
            <ActivityIndicator size="small" color="#fff" />
          ) : (
            <Text style={styles.sendBtnText}>→</Text>
          )}
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.background },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 16, paddingTop: 56, paddingBottom: 12,
    backgroundColor: Colors.primary,
  },
  backBtn: { fontSize: 28, color: '#fff', fontWeight: '600' },
  headerTitle: { fontSize: 18, fontWeight: '700', color: '#fff', flex: 1, textAlign: 'center' },
  messagesList: { paddingHorizontal: 16, paddingVertical: 12 },
  msgRow: { marginBottom: 8 },
  msgRowMe: { alignItems: 'flex-end' },
  msgRowOther: { alignItems: 'flex-start' },
  msgBubble: {
    maxWidth: '80%', paddingHorizontal: 14, paddingVertical: 10,
    borderRadius: 16,
  },
  msgBubbleMe: { backgroundColor: Colors.messageMe, borderBottomRightRadius: 4 },
  msgBubbleOther: { backgroundColor: Colors.messageOther, borderBottomLeftRadius: 4 },
  msgText: { fontSize: 16, color: '#fff', lineHeight: 22 },
  msgTime: { fontSize: 11, color: 'rgba(255,255,255,0.7)', marginTop: 4, alignSelf: 'flex-end' },
  emptyText: { color: Colors.textSecondary, fontSize: 16, textAlign: 'center', marginTop: 40 },
  typingIndicator: { fontSize: 12, color: Colors.textSecondary, paddingHorizontal: 20, marginBottom: 4, fontStyle: 'italic' },
  inputRow: {
    flexDirection: 'row', alignItems: 'flex-end', padding: 8,
    paddingBottom: 24, backgroundColor: Colors.surface, borderTopWidth: 1,
    borderTopColor: Colors.border,
  },
  input: {
    flex: 1, backgroundColor: Colors.background, borderRadius: 20,
    paddingHorizontal: 16, paddingVertical: 10, fontSize: 16,
    maxHeight: 100, color: Colors.text, marginRight: 8,
  },
  sendBtn: {
    width: 44, height: 44, borderRadius: 22, backgroundColor: Colors.primary,
    justifyContent: 'center', alignItems: 'center',
  },
  sendBtnDisabled: { opacity: 0.5 },
  sendBtnText: { color: '#fff', fontSize: 20, fontWeight: '600' },
});