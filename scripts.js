// 移动端导航栏通用解决方案
document.addEventListener('DOMContentLoaded', function() {
    // 直接获取汉堡按钮和导航菜单
    var hamburger = document.querySelector('.hamburger');
    var navMenu = document.querySelector('.nav-menu');
    
    // 确保元素存在
    if (!hamburger || !navMenu) return;
    
    // 为汉堡按钮添加事件监听器
    hamburger.addEventListener('click', function() {
        console.log('汉堡按钮被点击'); // 调试用
        hamburger.classList.toggle('active');
        navMenu.classList.toggle('active');
    });
    
    // 特别为移动设备添加触摸事件
    hamburger.addEventListener('touchend', function(e) {
        e.preventDefault(); // 防止点击事件同时触发
        console.log('汉堡按钮被触摸'); // 调试用
        hamburger.classList.toggle('active');
        navMenu.classList.toggle('active');
    });
    
    // 点击导航链接时关闭菜单
    var navLinks = document.querySelectorAll('.nav-link');
    navLinks.forEach(function(link) {
        link.addEventListener('click', function() {
            hamburger.classList.remove('active');
            navMenu.classList.remove('active');
        });
    });
    
    // 点击页面其他区域时关闭菜单
    document.addEventListener('click', function(e) {
        if (!hamburger.contains(e.target) && !navMenu.contains(e.target)) {
            hamburger.classList.remove('active');
            navMenu.classList.remove('active');
        }
    });
});

document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', function() {
        // 移除所有激活状态
        document.querySelectorAll('.tab-btn, .tab-content').forEach(el => {
            el.classList.remove('active');
        });
        
        // 添加当前激活状态
        this.classList.add('active');
        const tabId = this.dataset.tab;
        document.getElementById(tabId).classList.add('active');
    });
});