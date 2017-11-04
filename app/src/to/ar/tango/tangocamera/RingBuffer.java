package to.ar.tango.tangocamera;

import java.lang.reflect.Array;

// Hacked RingBuffer due to crippled Java templates.
public class RingBuffer<T>
//========================
{
   private T[] contents;
   private volatile int head, tail, length;
   private int count;
   private Class<T> type;

    public RingBuffer(Class<T> C, int count)
   //---------------------------------
   {
      @SuppressWarnings("unchecked")
      final T[] a = (T[]) Array.newInstance(C, count);
      contents = a;
      this.count = count;
      this.type = C;
      head = tail = length = 0;
   }

   private int indexIncrement(int i) { return (++i >= count) ? 0 : i; }
   private int indexDecrement(int i) { return (0 == i) ? (length - 1) : (i - 1);  }

   public synchronized void clear() { head = tail = length = 0; }

   public synchronized boolean isEmpty() { return (length == 0); }

   public synchronized boolean isFull() { return (length >= count); }

   public synchronized int push(T item)
   //----------------------------------------------------------------
   {
      if (length >= count)
      {
         tail = indexIncrement(tail);
         length--;
      }
      contents[head] = item;
      head = indexIncrement(head);
      length++;
      return count - length;
   }

   public synchronized T pop()
   //-----------------------------------------
   {
      T popped;
      if (length > 0)
      {
         popped = contents[tail];
         tail = indexIncrement(tail);
         length--;
      }
      else
         throw new IndexOutOfBoundsException("RingBuffer<" + type.getName() + ">.pop()");
      return popped;
   }

   public synchronized T peek()
   //-----------------------------------------
   {
      T popped;
      if (length > 0)
         popped = contents[tail];
      else
         throw new IndexOutOfBoundsException("RingBuffer<" + type.getName() + ">.peek()");
      return popped;
   }

   public synchronized T peek(T emptyVal)
   //------------------------------------
   {
      final T popped;
      if (length > 0)
         popped = contents[tail];
      else
         popped = emptyVal;
      return popped;
   }

   public synchronized T[] popAll()
   //----------------------------------------------
   {
      @SuppressWarnings("unchecked")
      final T[] a = (T[]) Array.newInstance(type, length);
      if (length > 0)
      {
         int i = 0;
         while (length > 0)
         {
            a[i++] = contents[tail];
            tail = indexIncrement(tail);
            length--;
         }
      }
      return a;
   }

   public synchronized T[] peekAll()
   //----------------------------------------------
   {
      @SuppressWarnings("unchecked")
      final T[] a = (T[]) Array.newInstance(type, length);
      if (length > 0)
      {
         int i = 0, len = length, t = tail;
         while (len > 0)
         {
            a[i++] = contents[t];
            t = indexIncrement(t);
            len--;
         }
      }
      return a;
   }
}
