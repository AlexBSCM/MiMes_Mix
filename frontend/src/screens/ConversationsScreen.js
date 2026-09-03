import React, { useCallback, useEffect, useState } from 'react';
import {
  View, Text, FlatList, TouchableOpacity, StyleSheet,
  ActivityIndicator, RefreshControl,
} from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { useAuth } from '../context/AuthContext';
import { fetchConversations } from '../api/conversations';
import { connectSocket, disconnectSocket, getSocket } from '../socket';
import { Colors } from '../theme';

export default function ConversationsScreen({ navigation }) {
  const { user, logout } = useAuth();
  const [conversations, setConversations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = async (refresh = false) => {
    if (refresh) setRefreshing(true);
    try {
      const data = await fetchConversations();
      setConversations(data);
    } catch (e) {
      console.warn('Load conversations error:', e.message);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useFocusEffect(
    useCallback(() => {
      load();
      // Подключаем socket при входе на экран диалогов
      const socket = connectSocket();
      socket.on('message:new', (msg) => {
        // Обновляем список, чтобы новый lastMessage отразился
        load();
      });
      return () => {
        socket.off('message:new');
      };
    }, [])
  );

  const renderItem = ({ item }) => {
    const lastMsg = item.messages?.[0];
    const lastText = lastMsg?.content
      ? (lastMsg.content.length > 50 ? lastMsg.content.slice(0, 50) + '...' : lastMsg.content)
      : 'Нет сообщений';
    const time = lastMsg?.createdAt
      ? new Date(lastMsg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      : '';

    return (
      <TouchableOpacity
        style={styles.chatItem}
        onPress={() => navigation.navigate('Chat', { conversation: item })}
      >
        <View style={styles.avatar}>
          <Text style={styles.avatarText}>
            {item.participants?.find((p) => p.user.id !== user?.id)?.user?.username?.charAt(0)?.toUpperCase() || '?'}
          </Text>
        </View>
        <View style={styles.chatInfo}>
          <View style={styles.chatHeader}>
            <Text style={styles.chatName} numberOfLines={1}>
              {item.name || item.participants?.find((p) => p.user.id !== user?.id)?.user?.username || 'Диалог'}
            </Text>
            {time ? <Text style={styles.chatTime}>{time}</Text> : null}
          </View>
          <Text style={styles.lastMessage} numberOfLines={1}>
            {lastText}
          </Text>
        </View>
      </TouchableOpacity>
    );
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>MiMes</Text>
        <TouchableOpacity onPress={logout} style={styles.logoutBtn}>
          <Text style={styles.logoutText}>Выйти</Text>
        </TouchableOpacity>
      </View>

      {loading ? (
        <ActivityIndicator size="large" color={Colors.primary} style={{ marginTop: 40 }} />
      ) : (
        <FlatList
          data={conversations}
          keyExtractor={(item) => item.id}
          renderItem={renderItem}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => load(true)} />}
          contentContainerStyle={conversations.length === 0 ? { flex: 1, justifyContent: 'center', alignItems: 'center' } : {}}
          ListEmptyComponent={<Text style={styles.emptyText}>Нет диалогов. Создайте чат с другим пользователем.</Text>}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.background },
  header: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    paddingHorizontal: 20, paddingTop: 60, paddingBottom: 16,
    backgroundColor: Colors.primary,
  },
  headerTitle: { fontSize: 24, fontWeight: '700', color: '#fff' },
  logoutBtn: { padding: 4 },
  logoutText: { color: '#fff', fontSize: 14, fontWeight: '600' },
  chatItem: {
    flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 12,
    backgroundColor: Colors.surface, marginHorizontal: 12, marginTop: 8,
    borderRadius: 12, elevation: 1, shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 4,
  },
  avatar: {
    width: 48, height: 48, borderRadius: 24, backgroundColor: Colors.primary,
    justifyContent: 'center', alignItems: 'center', marginRight: 12,
  },
  avatarText: { color: '#fff', fontSize: 20, fontWeight: '700' },
  chatInfo: { flex: 1 },
  chatHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 },
  chatName: { fontSize: 16, fontWeight: '600', color: Colors.text, flex: 1 },
  chatTime: { fontSize: 12, color: Colors.textSecondary, marginLeft: 8 },
  lastMessage: { fontSize: 14, color: Colors.textSecondary },
  emptyText: { color: Colors.textSecondary, fontSize: 16, textAlign: 'center', paddingHorizontal: 32 },
});