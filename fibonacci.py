def fibonacci(n):
    """
    计算斐波那契数列的第n项
    
    参数：
    n (int): 斐波那契数列的项数（从0开始）
    
    返回：
    int: 斐波那契数列的第n项
    
    示例：
    >>> fibonacci(0)
    0
    >>> fibonacci(1)
    1
    >>> fibonacci(10)
    55
    """
    if not isinstance(n, int) or n < 0:
        return None
    
    # 处理基本情况
    if n == 0:
        return 0
    elif n == 1:
        return 1
    
    # 使用迭代方法计算，避免递归的栈溢出问题
    a, b = 0, 1
    for _ in range(2, n + 1):
        a, b = b, a + b
    
    return b


def fibonacci_sequence(n):
    """
    生成斐波那契数列的前n项
    
    参数：
    n (int): 要生成的项数
    
    返回：
    list: 包含前n项斐波那契数的列表
    
    示例：
    >>> fibonacci_sequence(5)
    [0, 1, 1, 2, 3]
    """
    if not isinstance(n, int) or n < 0:
        return []
    
    # 处理特殊情况
    if n == 0:
        return []
    elif n == 1:
        return [0]
    
    # 生成数列
    sequence = [0, 1]
    
    if n == 2:
        return sequence
        
    for i in range(2, n):
        sequence.append(sequence[i-1] + sequence[i-2])
    
    return sequence


def fibonacci_with_cache():
    """
    使用闭包实现带缓存的斐波那契函数
    
    返回：
    function: 带缓存的斐波那契计算函数
    """
    cache = {0: 0, 1: 1}
    
    def fib_cached(n):
        if n < 0:
            return None
        if n in cache:
            return cache[n]
        
        # 使用动态规划自底向上计算
        for i in range(len(cache), n + 1):
            cache[i] = cache[i-1] + cache[i-2]
        
        return cache[n]
    
    return fib_cached


def fibonacci_recursive(n, memo=None):
    """
    递归方法计算斐波那契数（带备忘录优化）
    
    参数：
    n (int): 斐波那契数列的项数
    memo (dict): 备忘录缓存（内部使用）
    
    返回：
    int: 斐波那契数列的第n项
    """
    if n < 0:
        return None
    
    if memo is None:
        memo = {0: 0, 1: 1}
    
    if n in memo:
        return memo[n]
    
    memo[n] = fibonacci_recursive(n-1, memo) + fibonacci_recursive(n-2, memo)
    return memo[n]


def fibonacci_matrix(n):
    """
    使用矩阵快速幂算法计算大规模斐波那契数（O(log n)复杂度）
    
    这种方法特别适合计算非常大的斐波那契数
    基于公式：[[1, 1], [1, 0]]^n = [[F(n+1), F(n)], [F(n), F(n-1)]]
    """
    if n < 0:
        return None
    if n == 0:
        return 0
    if n == 1:
        return 1
    
    def matrix_multiply(a, b):
        """2x2矩阵乘法"""
        return [
            [a[0][0]*b[0][0] + a[0][1]*b[1][0], a[0][0]*b[0][1] + a[0][1]*b[1][1]],
            [a[1][0]*b[0][0] + a[1][1]*b[1][0], a[1][0]*b[0][1] + a[1][1]*b[1][1]]
        ]
    
    def matrix_power(matrix, power):
        """矩阵快速幂"""
        result = [[1, 0], [0, 1]]  # 单位矩阵
        base = matrix
        
        while power > 0:
            if power % 2 == 1:
                result = matrix_multiply(result, base)
            base = matrix_multiply(base, base)
            power //= 2
        
        return result
    
    # 斐波那契矩阵
    F = [[1, 1], [1, 0]]
    result_matrix = matrix_power(F, n-1)
    
    return result_matrix[0][0]


def fibonacci_binet(n):
    """
    使用Binet公式直接计算斐波那契数（可能有浮点误差）
    
    Binet公式：F(n) = (φ^n - ψ^n) / √5
    其中 φ = (1 + √5) / 2 ≈ 1.6180339887（黄金比例）
          ψ = (1 - √5) / 2 ≈ -0.6180339887
    """
    if n < 0:
        return None
    
    sqrt5 = 5 ** 0.5
    phi = (1 + sqrt5) / 2
    psi = (1 - sqrt5) / 2
    
    # 对于较小的n，使用浮点计算然后四舍五入
    result = (phi**n - psi**n) / sqrt5
    
    # 返回最接近的整数
    return int(round(result))


def fibonacci_generator():
    """
    生成斐波那契数列的无限生成器
    
    返回：
    generator: 无限生成斐波那契数的生成器
    
    示例：
    >>> fib_gen = fibonacci_generator()
    >>> next(fib_gen)  # 0
    >>> next(fib_gen)  # 1
    >>> next(fib_gen)  # 1
    """
    a, b = 0, 1
    yield a
    yield b
    
    while True:
        a, b = b, a + b
        yield b


# 测试代码
if __name__ == "__main__":
    # 测试基本函数
    test_cases = [0, 1, 5, 10, 20]
    
    print("斐波那契数列测试：")
    print("=" * 40)
    
    for n in test_cases:
        print(f"fibonacci({n}) = {fibonacci(n)}")
    
    print("\n" + "=" * 40)
    print("前10项斐波那契数列：")
    print(fibonacci_sequence(10))
    
    print("\n" + "=" * 40)
    print("不同算法比较（n=20）：")
    print(f"迭代方法: {fibonacci(20)}")
    
    # 创建缓存版本的函数并测试
    fib_cached = fibonacci_with_cache()
    print(f"缓存方法: {fib_cached(20)}")
    
    print(f"递归方法(带备忘录): {fibonacci_recursive(20)}")
    print(f"矩阵快速幂: {fibonacci_matrix(20)}")
    print(f"Binet公式: {fibonacci_binet(20)}")
    
    # 测试生成器
    print("\n" + "=" * 40)
    print("使用生成器生成前5个斐波那契数：")
    fib_gen = fibonacci_generator()
    first_five = [next(fib_gen) for _ in range(5)]
    print(first_five)
    
    # 性能测试（可选）
    print("\n" + "=" * 40)
    print("性能测试（计算第100项）：")
    
    import time
    
    # 测试迭代方法
    start = time.time()
    result1 = fibonacci(100)
    time1 = time.time() - start
    
    # 测试矩阵快速幂方法
    start = time.time()
    result2 = fibonacci_matrix(100)
    time2 = time.time() - start
    
    print(f"迭代方法: {result1} (用时: {time1:.6f}秒)")
    print(f"矩阵方法: {result2} (用时: {time2:.6f}秒)")
    
    # 验证结果是否一致
    if result1 == result2:
        print("✓ 两种方法结果一致")
    else:
        print("✗ 两种方法结果不一致")
    
    print("\n" + "=" * 40)
    print("测试完成！")