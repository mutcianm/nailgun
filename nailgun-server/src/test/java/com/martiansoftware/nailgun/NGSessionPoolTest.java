package com.martiansoftware.nailgun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NGSessionPoolTest {

  @Mock private NGServer server;

  @Test
  void takeOneSession() {
    NGSessionPool pool = new NGSessionPool(server, 1);
    NGSession session = pool.take();
    assertNotNull(session);
  }

  @Test
  void createNewSessions() {
    NGSessionPool pool = new NGSessionPool(server, 1);
    NGSession session1 = pool.take();
    NGSession session2 = pool.take();
    assertNotEquals(session1, session2);
  }

  @Test
  void reuseSessionPool() {
    NGSessionPool pool = new NGSessionPool(server, 1);
    NGSession session1 = pool.take();
    pool.give(session1);
    NGSession session2 = pool.take();
    assertEquals(session1, session2);
  }

  @Test
  void emptySessionPoolAlwaysCreateNewSession() {
    NGSessionPool pool = new NGSessionPool(server, 0);
    NGSession session1 = pool.take();
    pool.give(session1);
    NGSession session2 = pool.take();
    assertNotEquals(session1, session2);
  }

  @Test
  void shutdownTerminatesSession() throws InterruptedException {
    NGSession sessionMock = mock(NGSession.class);
    Supplier<NGSession> instanceCreatorMock = mock(Supplier.class);
    when(instanceCreatorMock.get()).thenReturn(sessionMock);
    NGSessionPool pool = new NGSessionPool(server, 1, instanceCreatorMock);
    NGSession session = pool.take();
    pool.shutdown();

    // check that shutdown is called on session
    verify(sessionMock, times(1)).shutdown();

    // this should not fail
    pool.give(session);
  }
}
