package io.moquette.persistence.redis;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 基于redis的组赛队列
 * 
 * @author yjwang
 *
 * @param <E>
 */
public class RedisQueue<E> implements BlockingQueue<E> {

    private RedisDao<E> redisDao;

    private String clientId;

    public RedisQueue(RedisDao<E> redisDao, String clientId) {
        this.redisDao = redisDao;
        this.clientId = "message:" + clientId;
    }

    @Override
    public E remove() {
        return redisDao.opsForList().leftPop(clientId);
    }

    @Override
    public E poll() {
        return redisDao.opsForList().leftPop(clientId);
    }

    @Override
    public E element() {
        return redisDao.opsForList().index(clientId, 0);
    }

    @Override
    public E peek() {
        return redisDao.opsForList().index(clientId, 0);
    }

    @Override
    public int size() {
        return redisDao.opsForList().size(clientId).intValue();
    }

    @Override
    public boolean isEmpty() {
        return redisDao.opsForList().size(clientId) == 0;
    }

    @Override
    public Iterator<E> iterator() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object[] toArray() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T> T[] toArray(T[] a) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void clear() {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean add(E e) {
        Long result = redisDao.opsForList().rightPush(clientId, e);
        return result.intValue() == 1;
    }

    @Override
    public boolean offer(E e) {
        E result = redisDao.opsForList().index(clientId, 0);

        if (result == null) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void put(E e) throws InterruptedException {
        redisDao.opsForList().rightPush(clientId, e);
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public E take() throws InterruptedException {
        return redisDao.opsForList().leftPop(clientId);
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int remainingCapacity() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean remove(Object o) {
        Long result = redisDao.opsForList().remove(clientId, 1, o);

        return result.intValue() == 1;
    }

    @Override
    public boolean contains(Object o) {
        return false;
    }

    @Override
    public int drainTo(Collection<? super E> c) {

        E result = redisDao.opsForList().leftPop(clientId);
        while (result != null) {
            c.add(result);
            result = redisDao.opsForList().leftPop(clientId);
        }

        return c.size();
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        E result = redisDao.opsForList().leftPop(clientId);

        int count = 0;

        while (result != null) {
            c.add(result);
            result = redisDao.opsForList().leftPop(clientId);
            count++;
            if (count > maxElements) {
                break;
            }
        }

        return c.size();
    }

}
