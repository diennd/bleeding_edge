// Copyright (c) 2012, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

#include "vm/message_handler.h"
#include "vm/port.h"
#include "vm/unit_test.h"

namespace dart {

class MessageHandlerTestPeer {
 public:
  explicit MessageHandlerTestPeer(MessageHandler* handler)
      : handler_(handler) {}

  void PostMessage(Message* message) { handler_->PostMessage(message); }
  void ClosePort(Dart_Port port) { handler_->ClosePort(port); }
  void CloseAllPorts() { handler_->CloseAllPorts(); }

  void increment_live_ports() { handler_->increment_live_ports(); }
  void decrement_live_ports() { handler_->decrement_live_ports(); }

  MessageQueue* queue() const { return handler_->queue_; }
  MessageQueue* oob_queue() const { return handler_->oob_queue_; }

 private:
  MessageHandler* handler_;

  DISALLOW_COPY_AND_ASSIGN(MessageHandlerTestPeer);
};


class TestMessageHandler : public MessageHandler {
 public:
  TestMessageHandler()
      : port_buffer_(NULL),
        port_buffer_size_(0),
        notify_count_(0),
        message_count_(0),
        start_called_(false),
        end_called_(false),
        result_(true) {
  }

  ~TestMessageHandler() {
    delete[] port_buffer_;
  }

  void MessageNotify(Message::Priority priority) {
    notify_count_++;
  }

  bool HandleMessage(Message* message) {
    // For testing purposes, keep a list of the ports
    // for all messages we receive.
    AddPortToBuffer(message->dest_port());
    delete message;
    message_count_++;
    return result_;
  }

  bool Start() {
    start_called_ = true;
    return true;
  }

  void End() {
    end_called_ = true;
    AddPortToBuffer(-2);
  }

  Dart_Port* port_buffer() const { return port_buffer_; }
  int notify_count() const { return notify_count_; }
  int message_count() const { return message_count_; }
  bool start_called() const { return start_called_; }
  bool end_called() const { return end_called_; }

  void set_result(bool result) { result_ = result; }

 private:
  void AddPortToBuffer(Dart_Port port) {
    if (port_buffer_ == NULL) {
      port_buffer_ = new Dart_Port[10];
      port_buffer_size_ = 10;
    } else if (message_count_ == port_buffer_size_) {
      int new_port_buffer_size_ = 2 * port_buffer_size_;
      Dart_Port* new_port_buffer_ = new Dart_Port[new_port_buffer_size_];
      for (int i = 0; i < port_buffer_size_; i++) {
        new_port_buffer_[i] = port_buffer_[i];
      }
      delete[] port_buffer_;
      port_buffer_ = new_port_buffer_;
      port_buffer_size_ = new_port_buffer_size_;
    }
    port_buffer_[message_count_] = port;
  }

  Dart_Port* port_buffer_;
  int port_buffer_size_;
  int notify_count_;
  int message_count_;
  bool start_called_;
  bool end_called_;
  bool result_;

  DISALLOW_COPY_AND_ASSIGN(TestMessageHandler);
};


bool TestStartFunction(uword data) {
  return (reinterpret_cast<TestMessageHandler*>(data))->Start();
}


void TestEndFunction(uword data) {
  return (reinterpret_cast<TestMessageHandler*>(data))->End();
}


UNIT_TEST_CASE(MessageHandler_PostMessage) {
  TestMessageHandler handler;
  MessageHandlerTestPeer handler_peer(&handler);
  EXPECT_EQ(0, handler.notify_count());

  // Post a message.
  Message* message = new Message(0, 0, NULL, 0, Message::kNormalPriority);
  handler_peer.PostMessage(message);

  // The notify callback is called.
  EXPECT_EQ(1, handler.notify_count());

  // The message has been added to the correct queue.
  EXPECT(message == handler_peer.queue()->Dequeue());
  EXPECT(NULL == handler_peer.oob_queue()->Dequeue());
  delete message;

  // Post an oob message.
  message = new Message(0, 0, NULL, 0, Message::kOOBPriority);
  handler_peer.PostMessage(message);

  // The notify callback is called.
  EXPECT_EQ(2, handler.notify_count());

  // The message has been added to the correct queue.
  EXPECT(message == handler_peer.oob_queue()->Dequeue());
  EXPECT(NULL == handler_peer.queue()->Dequeue());
  delete message;
}


UNIT_TEST_CASE(MessageHandler_ClosePort) {
  TestMessageHandler handler;
  MessageHandlerTestPeer handler_peer(&handler);
  Message* message1 = new Message(1, 0, NULL, 0, Message::kNormalPriority);
  handler_peer.PostMessage(message1);
  Message* message2 = new Message(2, 0, NULL, 0, Message::kNormalPriority);
  handler_peer.PostMessage(message2);

  handler_peer.ClosePort(1);

  // Closing the port does not drop the messages from the queue.
  EXPECT(message1 == handler_peer.queue()->Dequeue());
  EXPECT(message2 == handler_peer.queue()->Dequeue());
  delete message1;
  delete message2;
}


UNIT_TEST_CASE(MessageHandler_CloseAllPorts) {
  TestMessageHandler handler;
  MessageHandlerTestPeer handler_peer(&handler);
  Message* message1 = new Message(1, 0, NULL, 0, Message::kNormalPriority);
  handler_peer.PostMessage(message1);
  Message* message2 = new Message(2, 0, NULL, 0, Message::kNormalPriority);
  handler_peer.PostMessage(message2);

  handler_peer.CloseAllPorts();

  // All messages are dropped from the queue.
  EXPECT(NULL == handler_peer.queue()->Dequeue());
}


UNIT_TEST_CASE(MessageHandler_HandleNextMessage) {
  TestMessageHandler handler;
  MessageHandlerTestPeer handler_peer(&handler);
  Dart_Port port1 = PortMap::CreatePort(&handler);
  Dart_Port port2 = PortMap::CreatePort(&handler);
  Dart_Port port3 = PortMap::CreatePort(&handler);
  Message* message1 = new Message(port1, 0, NULL, 0, Message::kNormalPriority);
  handler_peer.PostMessage(message1);
  Message* oob_message1 = new Message(port2, 0, NULL, 0, Message::kOOBPriority);
  handler_peer.PostMessage(oob_message1);
  Message* message2 = new Message(port2, 0, NULL, 0, Message::kNormalPriority);
  handler_peer.PostMessage(message2);
  Message* oob_message2 = new Message(port3, 0, NULL, 0, Message::kOOBPriority);
  handler_peer.PostMessage(oob_message2);

  // We handle both oob messages and a single normal message.
  EXPECT(handler.HandleNextMessage());
  EXPECT_EQ(3, handler.message_count());
  Dart_Port* ports = handler.port_buffer();
  EXPECT_EQ(port2, ports[0]);
  EXPECT_EQ(port3, ports[1]);
  EXPECT_EQ(port1, ports[2]);
  PortMap::ClosePorts(&handler);
}


UNIT_TEST_CASE(MessageHandler_HandleOOBMessages) {
  TestMessageHandler handler;
  MessageHandlerTestPeer handler_peer(&handler);
  Dart_Port port1 = PortMap::CreatePort(&handler);
  Dart_Port port2 = PortMap::CreatePort(&handler);
  Dart_Port port3 = PortMap::CreatePort(&handler);
  Dart_Port port4 = PortMap::CreatePort(&handler);
  Message* message1 = new Message(port1, 0, NULL, 0, Message::kNormalPriority);
  handler_peer.PostMessage(message1);
  Message* message2 = new Message(port2, 0, NULL, 0, Message::kNormalPriority);
  handler_peer.PostMessage(message2);
  Message* oob_message1 = new Message(port3, 0, NULL, 0, Message::kOOBPriority);
  handler_peer.PostMessage(oob_message1);
  Message* oob_message2 = new Message(port4, 0, NULL, 0, Message::kOOBPriority);
  handler_peer.PostMessage(oob_message2);

  // We handle both oob messages but no normal messages.
  EXPECT(handler.HandleOOBMessages());
  EXPECT_EQ(2, handler.message_count());
  Dart_Port* ports = handler.port_buffer();
  EXPECT_EQ(port3, ports[0]);
  EXPECT_EQ(port4, ports[1]);
  handler_peer.CloseAllPorts();
}


struct ThreadStartInfo {
  MessageHandler* handler;
  Dart_Port* ports;
  int count;
};


static void SendMessages(uword param) {
  ThreadStartInfo* info = reinterpret_cast<ThreadStartInfo*>(param);
  MessageHandler* handler = info->handler;
  MessageHandlerTestPeer handler_peer(handler);
  for (int i = 0; i < info->count; i++) {
    Message* message =
        new Message(info->ports[i], 0, NULL, 0, Message::kNormalPriority);
    handler_peer.PostMessage(message);
  }
}


UNIT_TEST_CASE(MessageHandler_Run) {
  ThreadPool pool;
  TestMessageHandler handler;
  MessageHandlerTestPeer handler_peer(&handler);
  int sleep = 0;
  const int kMaxSleep = 20 * 1000;  // 20 seconds.

  EXPECT(!handler.HasLivePorts());
  handler_peer.increment_live_ports();

  handler.Run(&pool,
              TestStartFunction,
              TestEndFunction,
              reinterpret_cast<uword>(&handler));
  Dart_Port port = PortMap::CreatePort(&handler);
  Message* message = new Message(port, 0, NULL, 0, Message::kNormalPriority);
  handler_peer.PostMessage(message);

  // Wait for the first message to be handled.
  while (sleep < kMaxSleep && handler.message_count() < 1) {
    OS::Sleep(10);
    sleep += 10;
  }
  EXPECT_EQ(1, handler.message_count());
  EXPECT(handler.start_called());
  EXPECT(!handler.end_called());
  Dart_Port* handler_ports = handler.port_buffer();
  EXPECT_EQ(port, handler_ports[0]);

  // Start a thread which sends more messages.
  Dart_Port* ports = new Dart_Port[10];
  for (int i = 0; i < 10; i++) {
    ports[i] = PortMap::CreatePort(&handler);
  }
  ThreadStartInfo info;
  info.handler = &handler;
  info.ports = ports;
  info.count = 10;
  Thread::Start(SendMessages, reinterpret_cast<uword>(&info));
  while (sleep < kMaxSleep && handler.message_count() < 11) {
    OS::Sleep(10);
    sleep += 10;
  }
  handler_ports = handler.port_buffer();
  EXPECT_EQ(11, handler.message_count());
  EXPECT(handler.start_called());
  EXPECT(!handler.end_called());
  EXPECT_EQ(port, handler_ports[0]);
  for (int i = 1; i < 11; i++) {
    EXPECT_EQ(ports[i - 1], handler_ports[i]);
  }
  handler_peer.decrement_live_ports();
  EXPECT(!handler.HasLivePorts());
  PortMap::ClosePorts(&handler);
}

}  // namespace dart
