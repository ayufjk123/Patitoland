// 移动端导航栏通用解决方案
document.addEventListener('DOMContentLoaded', () => {
    // 通用菜单控制器
    const menuController = () => {
        const hamburgers = document.querySelectorAll('.hamburger');
        const navMenus = document.querySelectorAll('.nav-menu');

        hamburgers.forEach((hamburger, index) => {
            const navMenu = navMenus[index];

            const toggleMenu = (e) => {
                e.stopPropagation();
                hamburger.classList.toggle('active');
                navMenu.classList.toggle('active');
            };

            // 桌面端+移动端双事件支持
            hamburger.addEventListener('click', toggleMenu);
            hamburger.addEventListener('touchstart', toggleMenu);

            // 自动关闭逻辑
            document.addEventListener('click', (e) => {
                if (!e.target.closest('.nav-container')) {
                    hamburger.classList.remove('active');
                    navMenu.classList.remove('active');
                }
            });

            // 菜单项点击处理
            navMenu.querySelectorAll('.nav-link').forEach(link => {
                link.addEventListener('click', () => {
                    hamburger.classList.remove('active');
                    navMenu.classList.remove('active');
                });
            });
        });
    };

    // 初始化所有页面菜单
    menuController();

    // 动态加载页面兼容处理
    if (window.attachEvent) {
        window.attachEvent('onload', menuController);
    } else {
        if (window.onload) {
            const currentOnload = window.onload;
            window.onload = function(e) {
                currentOnload(e);
                menuController();
            };
        } else {
            window.onload = menuController;
        }
    }
});